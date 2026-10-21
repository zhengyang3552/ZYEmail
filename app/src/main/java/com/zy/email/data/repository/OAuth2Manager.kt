package com.zy.email.data.repository

import android.util.Base64
import android.util.Log
import com.zy.email.data.model.Account
import com.zy.email.data.model.AccountType
import com.zy.email.data.model.OAuth2Config
import com.zy.email.data.model.getImapDefaults
import com.zy.email.data.model.getOAuth2Defaults
import com.zy.email.data.model.getSmtpDefaults
import com.zy.email.data.model.resolveRedirectUri
import com.zy.email.utils.EncryptionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/** 授权回调结果：浏览器重定向到回环地址后解析出的两种 outcome */
sealed interface OAuth2RedirectResult {
    data class Authorized(val code: String, val state: String?) : OAuth2RedirectResult
    data class Denied(val error: String, val errorDescription: String?) : OAuth2RedirectResult
}

/** 用户在超时时间内没有完成授权 */
class OAuth2AuthorizationTimeoutException : IOException("授权超时，请重试")

/**
 * OAuth2 交互式授权管理。
 *
 * 完整流程（PKCE 授权码模式，无需 client_secret，适用于公共客户端）：
 *
 *  1. [startAuthorization]：在 127.0.0.1 上绑定一个临时端口，启动回调服务器；
 *     生成 PKCE code_verifier / state，拼出授权地址交给界面打开浏览器。
 *  2. 用户在浏览器中完成登录与授权，服务商重定向到回环地址
 *     （http://localhost:<port>/oauth2redirect），由 [LoopbackServer] 捕获授权码。
 *  3. [awaitAuthorizationResult]：等待回调结果，校验 state 防止 CSRF。
 *  4. [exchangeAuthorizationCode]：用授权码 + code_verifier 向 token 端点换取
 *     access_token / refresh_token。
 *  5. [getValidAccessToken]：IMAP/SMTP 连接前获取有效令牌，过期时自动用
 *     refresh_token 续期并回写数据库。
 *
 * 令牌存储：复用 [Account.password] 字段（加密后的 token JSON），
 * 不改动 Room 表结构；加解密沿用 [EncryptionUtils]。
 *
 * 交互式授权使用外部浏览器而非内嵌 WebView：Google 会拒绝 WebView 中的
 * OAuth 流程（"This browser or app may not be secure"），Microsoft 也建议
 * 使用系统浏览器以保证凭据安全。
 */
object OAuth2Manager {

    private const val TAG = "OAuth2Manager"

    /** 等待用户在浏览器中完成授权的最长时间 */
    private const val AUTH_TIMEOUT_MS = 10 * 60 * 1000L

    /** 令牌提前刷新余量，避免临界过期导致请求失败 */
    private const val EXPIRY_MARGIN_MS = 60 * 1000L

    /** 回调路径，与授权重定向模板中的占位一致 */
    private const val CALLBACK_PATH = "/oauth2redirect"

    // ------------------------------------------------------------------
    // 对外数据结构
    // ------------------------------------------------------------------

    /** 授权发起结果：授权地址交给界面打开浏览器 */
    data class AuthorizationStart(
        val authorizationUrl: String,
        val redirectUri: String
    )

    /** 授权成功后换取到的令牌集合 */
    data class TokenSet(
        val accessToken: String,
        val refreshToken: String?,
        /** 过期时间（epoch 毫秒） */
        val expiresAt: Long
    )

    // ------------------------------------------------------------------
    // 进行中的授权流程
    // ------------------------------------------------------------------

    private data class PendingFlow(
        val email: String,
        val displayName: String,
        val clientId: String,
        val config: OAuth2Config,
        val codeVerifier: String,
        val state: String,
        val redirectUri: String
    )

    @Volatile
    private var pending: PendingFlow? = null

    private var server: LoopbackServer? = null
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 刷新令牌互斥锁：并发收发信时避免重复刷新 */
    private val refreshMutex = Mutex()

    // ------------------------------------------------------------------
    // 交互式授权
    // ------------------------------------------------------------------

    /**
     * 发起交互式授权。
     *
     * @throws IllegalArgumentException 域名不支持 OAuth2，或 Client ID 为空
     */
    fun startAuthorization(email: String, clientId: String, displayName: String): AuthorizationStart {
        cancelAuthorization()

        require(clientId.isNotBlank()) { "Client ID 不能为空" }
        val config = getOAuth2Defaults(email, clientId)
            ?: throw IllegalArgumentException("该邮箱服务商暂不支持 OAuth2 授权登录")

        val callbackServer = LoopbackServer()
        val redirectUri = resolveRedirectUri(config, callbackServer.port)
        val codeVerifier = generateRandomString()
        val state = generateRandomString()

        pending = PendingFlow(email, displayName, clientId, config, codeVerifier, state, redirectUri)
        server = callbackServer
        callbackServer.start(managerScope)

        val url = buildAuthorizationUrl(config, clientId, redirectUri, codeVerifier, state, email)
        Log.d(TAG, "OAuth2 授权已发起：redirectUri=$redirectUri")
        return AuthorizationStart(url, redirectUri)
    }

    /**
     * 等待浏览器回调结果。
     *
     * @throws OAuth2AuthorizationTimeoutException 超时未完成授权
     * @throws IllegalStateException state 校验失败
     */
    suspend fun awaitAuthorizationResult(timeoutMs: Long = AUTH_TIMEOUT_MS): OAuth2RedirectResult {
        val callbackServer = server ?: throw IllegalStateException("尚未发起 OAuth2 授权")

        val result = withTimeoutOrNull(timeoutMs) { callbackServer.results.receive() }
        if (result == null) {
            cancelAuthorization()
            throw OAuth2AuthorizationTimeoutException()
        }

        if (result is OAuth2RedirectResult.Authorized) {
            val flow = pending
            if (flow == null || (!result.state.isNullOrBlank() && result.state != flow.state)) {
                cancelAuthorization()
                throw IllegalStateException("授权回调校验失败（state 不匹配），请重试")
            }
        }
        return result
    }

    /**
     * 用授权码换取令牌，并基于进行中的授权流程组装待验证的账户。
     * 成功后结束回调服务器并清空流程状态。
     */
    suspend fun exchangeAuthorizationCode(code: String): Pair<Account, TokenSet> {
        val flow = pending ?: throw IllegalStateException("没有进行中的 OAuth2 授权流程")

        val json = postForm(
            flow.config.tokenUrl,
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to flow.redirectUri,
                "client_id" to flow.clientId,
                "code_verifier" to flow.codeVerifier
            )
        )
        if (json.has("error")) {
            throw IOException("令牌交换失败：${json.optString("error")}")
        }

        val refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
        if (refreshToken == null) {
            throw IOException("服务商未返回刷新令牌：Microsoft 请确认 scope 含 offline_access，Google 请确认应用类型为「桌面应用」")
        }

        val tokens = TokenSet(
            accessToken = json.getString("access_token"),
            refreshToken = refreshToken,
            expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L
        )

        // 组装账户（password 暂存明文 token JSON，落库时由 Repository 统一加密）
        val (smtpServer, smtpPort) = getSmtpDefaults(flow.email)
        val (imapServer, imapPort) = getImapDefaults(flow.email)
        val account = Account(
            email = flow.email,
            displayName = flow.displayName,
            password = encodeTokens(tokens),
            accountType = AccountType.OAUTH2,
            smtpServer = smtpServer,
            smtpPort = smtpPort,
            smtpSecure = if (smtpPort == 587) "TLS" else "SSL",
            imapServer = imapServer,
            imapPort = imapPort,
            imapSecure = "SSL",
            clientId = flow.clientId,
            authorizeUrl = flow.config.authorizeUrl,
            tokenUrl = flow.config.tokenUrl,
            scope = flow.config.scope,
            redirectUri = flow.redirectUri
        )

        cancelAuthorization()
        return account to tokens
    }

    /** 用户取消 / 页面销毁时终止授权流程 */
    fun cancelAuthorization() {
        server?.close()
        server = null
        pending = null
    }

    // ------------------------------------------------------------------
    // 令牌获取与刷新
    // ------------------------------------------------------------------

    /**
     * 获取账户当前有效的 access token；过期时自动刷新并回写数据库。
     *
     * @throws IOException 刷新失败（如 refresh_token 已被吊销）
     */
    suspend fun getValidAccessToken(account: Account): String = withContext(Dispatchers.IO) {
        require(account.accountType == AccountType.OAUTH2) { "账户类型不是 OAUTH2" }

        var tokens = decodeTokens(account.password)
        if (System.currentTimeMillis() < tokens.expiresAt - EXPIRY_MARGIN_MS) {
            return@withContext tokens.accessToken
        }

        refreshMutex.withLock {
            // 双重检查：等锁期间可能已被其他连接刷新过
            tokens = decodeTokens(account.password)
            if (System.currentTimeMillis() < tokens.expiresAt - EXPIRY_MARGIN_MS) {
                return@withContext tokens.accessToken
            }

            val oldRefresh = tokens.refreshToken
                ?: throw IOException("授权已过期且没有可用的刷新令牌，请删除账户后重新授权")

            val json = postForm(
                account.tokenUrl,
                mapOf(
                    "grant_type" to "refresh_token",
                    "refresh_token" to oldRefresh,
                    "client_id" to account.clientId
                )
            )
            if (json.has("error")) {
                throw IOException("令牌刷新失败：${json.optString("error")}，请删除账户后重新授权")
            }

            val refreshed = TokenSet(
                accessToken = json.getString("access_token"),
                // 部分服务商刷新时不返回新 refresh_token，继续沿用旧的
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: oldRefresh,
                expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L
            )
            persistTokens(account, refreshed)
            refreshed.accessToken
        }
    }

    /** 刷新成功后把新令牌回写数据库（仅对已落库的账户） */
    private suspend fun persistTokens(account: Account, tokens: TokenSet) {
        if (account.id <= 0) return
        try {
            val updated = account.copy(
                password = EncryptionUtils.encrypt(encodeTokens(tokens)),
                updatedAt = System.currentTimeMillis()
            )
            RoomDatabaseManager.updateAccount(updated)
            Log.d(TAG, "OAuth2 令牌已刷新并回写数据库：${account.email}")
        } catch (e: Exception) {
            // 回写失败不影响本次连接，下次会再尝试刷新
            Log.w(TAG, "OAuth2 令牌回写失败", e)
        }
    }

    // ------------------------------------------------------------------
    // 令牌序列化
    // ------------------------------------------------------------------

    fun encodeTokens(tokens: TokenSet): String = JSONObject().apply {
        put("access_token", tokens.accessToken)
        put("refresh_token", tokens.refreshToken.orEmpty())
        put("expires_at", tokens.expiresAt)
    }.toString()

    /**
     * 解析账户中的 token JSON。
     * 兼容两种来源：首次登录验证时尚未加密（明文 JSON），已落库的为加密串。
     */
    fun decodeTokens(stored: String): TokenSet {
        val json = runCatching { JSONObject(stored) }
            .recoverCatching { JSONObject(EncryptionUtils.decrypt(stored)) }
            .getOrElse { throw IOException("OAuth2 令牌数据损坏，请删除账户后重新授权") }

        val accessToken = json.optString("access_token").takeIf { it.isNotBlank() }
            ?: throw IOException("OAuth2 令牌缺失 access_token，请重新授权")
        return TokenSet(
            accessToken = accessToken,
            refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
            expiresAt = json.optLong("expires_at", 0L)
        )
    }

    // ------------------------------------------------------------------
    // 授权地址构造
    // ------------------------------------------------------------------

    private fun buildAuthorizationUrl(
        config: OAuth2Config,
        clientId: String,
        redirectUri: String,
        codeVerifier: String,
        state: String,
        email: String
    ): String {
        val challenge = base64Url(
            MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        )
        val isGoogle = config.authorizeUrl.contains("accounts.google.com")

        val params = linkedMapOf(
            "response_type" to "code",
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "scope" to config.scope,
            "state" to state,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "login_hint" to email
        )
        if (isGoogle) {
            // Google 必须显式要求离线访问才能拿到 refresh_token
            params["access_type"] = "offline"
            params["prompt"] = "consent"
        } else {
            params["response_mode"] = "query"
        }
        return config.authorizeUrl + "?" + params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
    }

    // ------------------------------------------------------------------
    // HTTP：令牌端点
    // ------------------------------------------------------------------

    private suspend fun postForm(tokenUrl: String, params: Map<String, String>): JSONObject =
        withContext(Dispatchers.IO) {
            val body = params.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            val connection = URL(tokenUrl).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.setRequestProperty("Accept", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val error = runCatching { JSONObject(text).optString("error") }.getOrNull()
                    throw IOException("令牌端点请求失败（HTTP $status${error?.let { "：$it" } ?: ""}）")
                }
                JSONObject(text)
            } finally {
                connection.disconnect()
            }
        }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 生成 base64url 随机串（PKCE verifier / state 共用） */
    private fun generateRandomString(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    // ------------------------------------------------------------------
    // 回环回调服务器
    // ------------------------------------------------------------------

    /**
     * 极简 HTTP 回调服务器：绑定 127.0.0.1 随机端口，接收浏览器授权后的
     * 重定向请求，从 query 中提取 code / error。
     *
     * Microsoft 公共客户端允许 http://localhost 任意端口；Google「桌面应用」
     * 类型客户端内置支持 http://127.0.0.1 回环重定向，因此两个服务商都
     * 无需修改 Manifest 即可完成交互式授权。
     */
    private class LoopbackServer {

        private var serverSocket: ServerSocket? = null
        val port: Int
        val results = Channel<OAuth2RedirectResult>(Channel.UNLIMITED)

        init {
            val socket = ServerSocket()
            socket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
            serverSocket = socket
            port = socket.localPort
        }

        private var acceptJob: Job? = null

        fun start(scope: CoroutineScope) {
            acceptJob = scope.launch {
                val socket = serverSocket ?: return@launch
                try {
                    while (!socket.isClosed) {
                        val client = socket.accept()
                        launch { handleClient(client) }
                    }
                } catch (_: Exception) {
                    // 服务器被关闭时 accept 抛异常退出，属正常路径
                }
            }
        }

        fun close() {
            acceptJob?.cancel()
            runCatching { serverSocket?.close() }
        }

        private suspend fun handleClient(client: Socket) {
            client.use { socket ->
                try {
                    socket.soTimeout = 10_000
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
                    val requestLine = reader.readLine() ?: return
                    // 读完请求头，避免浏览器连接复用出现问题
                    var line: String? = reader.readLine()
                    while (!line.isNullOrBlank()) {
                        line = reader.readLine()
                    }

                    val target = requestLine.split(" ").getOrNull(1)
                    val uri = try {
                        target?.let { java.net.URI("http://localhost$it") }
                    } catch (_: Exception) {
                        null
                    }
                    if (uri == null || uri.path != CALLBACK_PATH) {
                        // 顺手处理 favicon 之类的无关请求
                        writeResponse(socket, 404, NOT_FOUND_HTML)
                        return
                    }

                    val params = parseQuery(uri.rawQuery)
                    val result = when {
                        params.containsKey("error") -> OAuth2RedirectResult.Denied(
                            params["error"].orEmpty(),
                            params["error_description"]
                        )
                        params.containsKey("code") -> OAuth2RedirectResult.Authorized(
                            params["code"].orEmpty(),
                            params["state"]
                        )
                        else -> OAuth2RedirectResult.Denied("invalid_request", null)
                    }
                    writeResponse(socket, 200, SUCCESS_HTML)
                    results.trySend(result)
                } catch (_: Exception) {
                    // 单个连接处理失败不影响整体流程
                }
            }
        }

        private fun parseQuery(query: String?): Map<String, String> {
            if (query.isNullOrBlank()) return emptyMap()
            return query.split("&")
                .mapNotNull { pair ->
                    val idx = pair.indexOf('=')
                    if (idx <= 0) return@mapNotNull null
                    val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                    val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    key to value
                }
                .toMap()
        }

        private fun writeResponse(socket: Socket, status: Int, html: String) {
            val body = html.toByteArray(Charsets.UTF_8)
            val header = "HTTP/1.1 $status ${if (status == 200) "OK" else "Not Found"}\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${body.size}\r\n" +
                    "Connection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(header.toByteArray(Charsets.US_ASCII))
                write(body)
                flush()
            }
        }

        private companion object {
            val SUCCESS_HTML = """
                <html><head><meta charset="utf-8"><title>授权完成</title></head>
                <body style="font-family:sans-serif;text-align:center;padding-top:80px">
                <h2>授权成功</h2>
                <p>请返回 ZYEmail 继续操作，本页面可以关闭。</p>
                </body></html>
            """.trimIndent()

            val NOT_FOUND_HTML = """
                <html><head><meta charset="utf-8"></head>
                <body style="font-family:sans-serif;text-align:center;padding-top:80px">Not Found</body></html>
            """.trimIndent()
        }
    }
}
