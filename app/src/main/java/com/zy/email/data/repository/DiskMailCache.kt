package com.zy.email.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

/**
 * 邮件列表磁盘缓存。
 *
 * 目的：冷启动或重新进入邮件列表时先展示上次的内容，再后台刷新
 * （Stale-While-Revalidate），避免每次都白屏等待网络。
 *
 * 存储位置：context.cacheDir/mail_cache/<accountId>_<folder>.json
 * （系统缓存目录，空间不足时可被系统回收，无需手动清理；
 * 账户删除时由调用方主动清除对应文件）。
 *
 * 用 Gson 序列化 DTO（日期存 epoch 毫秒，避免 Gson 默认 Date 格式丢精度）。
 */
object DiskMailCache {

    private const val TAG = "DiskMailCache"
    private const val DIR_NAME = "mail_cache"

    private val gson = Gson()

    /** 缓存 DTO：日期以毫秒存储，规避 Gson 默认 Date 格式的精度损失 */
    private data class CachedMessage(
        val uid: Long,
        val subject: String,
        val from: String,
        val to: String,
        val cc: String,
        val dateMillis: Long,
        val bodyText: String,
        val bodyHtml: String,
        val isRead: Boolean,
        val hasAttachments: Boolean,
        val messageId: String?
    )

    private fun fileFor(context: Context, accountId: Long, folder: MailFolder): File =
        File(File(context.cacheDir, DIR_NAME).apply { mkdirs() }, "${accountId}_${folder.name}.json")

    /** 读取缓存；无缓存或解析失败返回 null（失败视为无缓存） */
    suspend fun load(context: Context, accountId: Long, folder: MailFolder): List<EmailMessage>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = fileFor(context, accountId, folder)
                if (!file.exists()) return@runCatching null

                val type = object : TypeToken<List<CachedMessage>>() {}.type
                val cached: List<CachedMessage> = gson.fromJson(file.readText(), type)
                cached.map {
                    EmailMessage(
                        uid = it.uid,
                        subject = it.subject,
                        from = it.from,
                        to = it.to,
                        cc = it.cc,
                        date = if (it.dateMillis > 0L) Date(it.dateMillis) else null,
                        bodyText = it.bodyText,
                        bodyHtml = it.bodyHtml,
                        isRead = it.isRead,
                        hasAttachments = it.hasAttachments,
                        messageId = it.messageId
                    )
                }
            }.onFailure { Log.w(TAG, "读取邮件缓存失败: ${it.message}") }.getOrNull()
        }

    /** 写入缓存；失败静默（缓存不可用不影响主流程） */
    suspend fun save(context: Context, accountId: Long, folder: MailFolder, messages: List<EmailMessage>) =
        withContext(Dispatchers.IO) {
            runCatching {
                val dto = messages.map {
                    CachedMessage(
                        it.uid, it.subject, it.from, it.to, it.cc,
                        it.date?.time ?: 0L, it.bodyText, it.bodyHtml,
                        it.isRead, it.hasAttachments, it.messageId
                    )
                }
                fileFor(context, accountId, folder).writeText(gson.toJson(dto))
            }.onFailure { Log.w(TAG, "写入邮件缓存失败: ${it.message}") }
        }

    /** 删除账户时清理其全部缓存文件 */
    suspend fun deleteAll(context: Context, accountId: Long) = withContext(Dispatchers.IO) {
        MailFolder.entries.forEach { folder ->
            runCatching { fileFor(context, accountId, folder).delete() }
        }
    }
}
