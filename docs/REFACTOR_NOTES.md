# ZYEmail 改造说明（Material 3 主题 · 布局交互 · 核心功能健壮性）

本次改造在保持 **Kotlin + MVVM + ViewBinding + Room + JavaMail** 技术栈与目录结构不变的前提下完成，
对外可见的行为增强，对内则是修复了一批"会让功能实际不可用"的问题。

---

## 一、配色方案与设计依据

### 1.1 种子色与推导方式

| 项 | 取值 | 说明 |
| --- | --- | --- |
| 种子色（seed） | `#0B57D0` | Google Material 3 基础蓝，与原项目主色 `#1976D2` 同色相，视觉延续但饱和度更收敛 |
| 色相 | ≈ 215°（蓝） | 邮件类工具需要"可信、克制、专注"，蓝色是 M3 生产力场景的基准色相 |
| 推导方式 | M3 Tonal Palette（色调映射） | 同一色相取不同 tone 承担不同角色：浅色主色 tone 40、容器 tone 90；深色主色 tone 80、容器 tone 30 |

### 1.2 为什么不是"简单反色"

Material 3 的深色模式是重新映射色调，而不是把 RGB 取反：

- 浅色方案中 `primary = #0B57D0`（tone 40）在深色背景上对比度不足，深色方案改用 `#A9C7FF`（tone 80）；
- 容器色反向变化：浅色 `primary_container = #D3E3FD`（tone 90，浅底深字），深色 `= #0842A0`（tone 30，深底浅字）；
- 表面从 `#111318`（tone 6）起逐级抬升到 `#33353A`，用"表面层级"表达卡片高低，替代阴影——深色背景上阴影几乎不可见。

### 1.3 语义化颜色角色（39 项令牌）

| 角色 | 浅色 | 深色 | 在 ZYEmail 中的落位 |
| --- | --- | --- | --- |
| `primary` / `on_primary` | `#0B57D0` / `#FFF` | `#A9C7FF` / `#003062` | 主按钮、未读圆点、链接、选中态 |
| `primary_container` / `on_...` | `#D3E3FD` / `#041E49` | `#0842A0` / `#D3E3FD` | 扩展 FAB、品牌图标底、发件人头像底 |
| `secondary` / `on_secondary` | `#3E5F8A` / `#FFF` | `#B3C7E8` / `#1C3450` | 次级动作、标签 |
| `secondary_container` / `on_...` | `#D6E3FF` / `#001B3D` | `#35496A` / `#D6E3FF` | 中性提示块 |
| `tertiary` / `on_tertiary` | `#006A67` / `#FFF` | `#82D5D1` / `#003735` | 附件图标等"非错误但需注意"的标记 |
| `tertiary_container` / `on_...` | `#9EF2ED` / `#00201F` | `#004F4D` / `#9EF2ED` | 附件标签底 |
| `error` / `on_error` | `#B3261E` / `#FFF` | `#FFB4AB` / `#690005` | 表单错误、删除账户、收信失败 |
| `error_container` / `on_...` | `#F9DEDC` / `#410E0B` | `#93000A` / `#FFDAD6` | 错误横幅 |
| `surface` / `on_surface` | `#F8FAFD` / `#1A1C1E` | `#111318` / `#E2E2E6` | 页面底色、工具栏、状态栏 |
| `surface_variant` / `on_...` | `#DFE2EB` / `#43474E` | `#43474E` / `#C3C7CF` | 次级文字、次级表面 |
| `surface_container_low` | `#F2F4F8` | `#191C20` | 已读邮件卡片、设置分组 |
| `surface_container` | `#ECEEF3` | `#1D2024` | 未读邮件卡片（高一级，暗示需要关注） |
| `surface_container_high/highest` | `#E6E8EE` / `#E0E3E9` | `#282A2F` / `#33353A` | 对话框、进度条轨道 |
| `outline` / `outline_variant` | `#73777F` / `#C3C7CF` | `#8D9199` / `#43474E` | 1dp 卡片描边、分隔线 |
| `success` 系列（扩展） | `#146C2E` / `#C4EED0` | `#6DD58C` / `#00522A` | 发送成功、验证通过 |
| `warning` 系列（扩展） | `#8B5000` / `#FFDCC0` | `#FFB865` / `#653D00` | HTML 渲染降级、配置提醒 |

> `success` / `warning` 是 M3 规范之外按同一色调方法扩展的业务语义色——M3 只内置 `error`，
> 而邮件场景确实需要"成功 / 提醒"两级状态。

### 1.4 对比度实测（WCAG 2.1）

正文文本要求 ≥ 4.5:1。实测结果（由两套 `colors.xml` 的实际色值计算）：

| 组合 | 浅色 | 深色 |
| --- | --- | --- |
| `on_primary` / `primary` | 6.39:1 | 7.69:1 |
| `on_primary_container` / `primary_container` | 12.57:1 | 7.04:1 |
| `on_secondary` / `secondary` | 6.54:1 | 7.40:1 |
| `on_tertiary` / `tertiary` | 6.45:1 | 7.75:1 |
| `on_error` / `error` | 6.54:1 | 7.72:1 |
| `on_surface` / `surface` | 16.34:1 | 14.38:1 |
| `on_surface_variant` / `surface` | 8.93:1 | 10.96:1 |
| `on_surface_variant` / `surface_container` | 8.04:1 | 9.65:1 |
| `primary` / `surface`（文字） | 6.11:1 | 10.88:1 |
| `outline` / `surface`（非文本） | 4.30:1 | 5.88:1 |

全部文本组合达到 AA；浅色 `outline` 为 4.30:1，仅用于 1dp 描边，满足非文本元素 ≥ 3:1 的要求。

### 1.5 令牌化的其他两轴

- **间距**：`space_1..9`（4/8/12/16/20/24/32/40/48dp），全部布局只引用令牌，不再出现 8/12/16/24 混排。
- **形状**：对齐 M3 Shape Scale —— 4/8/12/16/28dp；列表项 12dp、账户卡片 16dp、登录表单卡片 28dp。
- **字体**：实现 M3 Type Scale 的 9 级（HeadlineSmall → LabelSmall），每级固定字号与字距，按钮统一 `LabelLarge` 且不再全大写。

### 1.6 明暗模式落地方式

- 资源层：`values/colors.xml` 与 `values-night/colors.xml` 定义**同名**令牌，系统在 uiMode 变化时自动换用。
- 偏好层：`ThemeManager` 把"跟随系统 / 浅色 / 深色"持久化到 SharedPreferences，在 `Application#onCreate` 中调用
  `AppCompatDelegate.setDefaultNightMode`，避免"先亮后暗"的闪烁；设置页切换后由框架重建界面。
- 组件层：主题属性（`colorPrimary`、`colorSurface`、`colorOnSurfaceVariant` 等）全部指向语义令牌，
  Material 组件（输入框、Chip、对话框、Snackbar）自动继承，无需逐个改样式。

---

## 二、布局与交互改造

| 页面 | 改造要点 |
| --- | --- |
| 主界面 | 标题栏 + 顶部线性进度条；账户/邮件列表统一卡片化（12~16dp 圆角、1dp 描边、无阴影）；空状态与错误状态共用"图标 + 标题 + 说明 + 操作按钮"结构；扩展 FAB（主色容器）仅在邮件列表出现；返回键语义修正为"邮件列表→账户列表→退出" |
| 登录页 | 品牌标识（主色容器圆形）+ 标题 + 表单卡片（28dp 圆角）三段式；字段顺序改为 邮箱→密码→显示名称；错误就近显示在输入框而非 Toast；主按钮单一强调层级；OAuth2 域名提供"应用专用密码"开关 |
| 写信页 | 固定标题栏 + 可滚动字段区 + 固定底部操作栏；抄送/密送改为容器外的过滤 Chip（修复原"永远无法展开"的缺陷）；正文区自动撑满；发送期间禁用按钮并显示进度 |
| 邮件详情 | 主题（最多 3 行）→ 发件信息卡 → 快捷操作 → 正文；HTML/纯文本互斥渲染；邮件正文注入与主题一致的排版样式（含深色）；收件人行可点击展开；回复/全部回复/转发真实可用 |
| 设置页 | 原生布局替代 PreferenceFragment（原实现因 NoActionBar 主题导致无标题无返回）；账户卡片带删除入口与二次确认；新增外观三档切换；版本信息 |
| 列表项 | 邮件项：字母头像 + 发件人 + 主题 + 预览 + 时间 + 未读圆点 + 附件图标；未读用"加粗 + 圆点 + 更高表面层级"三重信号，不依赖单一颜色 |

其他：全部中文文案移入 `strings.xml`（原布局中存在 20+ 处硬编码）；工具栏菜单图标按 `onSurfaceVariant` 着色，
解决深色/浅色下白色图标不可见的问题；触控目标统一 ≥ 48dp。

---

## 三、核心功能梳理与异常处理

### 3.1 修复的阻断性问题

| # | 问题 | 影响 | 处理 |
| --- | --- | --- | --- |
| 1 | `AccountDao` 查询写 `ORDER BY created_at`，实体字段为 `createdAt` | Room 编译期报"列不存在"，工程无法构建 | 改为 `createdAt` |
| 2 | `MainActivity` 引用 `binding.progressBar`，布局中无此控件 | 编译失败 | 补 `LinearProgressIndicator` |
| 3 | Room 用 `annotationProcessor` 处理 Kotlin 实体 | DAO 实现类不会生成，运行时崩 | 改用 `kapt` |
| 4 | 缺少 `lifecycle-runtime-ktx` / `lifecycle-viewmodel-ktx` / `activity-ktx` | `lifecycleScope`、`viewModelScope`、`by viewModels()` 无法解析 | 显式声明依赖 |
| 5 | 登录/收信在 `lifecycleScope.launch`（主线程）直接跑 IMAP/SMTP | `NetworkOnMainThreadException`，登录与收信必然失败 | Repository 内统一 `withContext(Dispatchers.IO)` |
| 6 | `OAuth2Manager` 调用了 MSAL 不存在的 API，`callbackFlow` 从不发事件；ViewModel 直接置"授权成功" | 假授权，用户带着不可用账户进入主界面 | 移除不可用封装与 MSAL 依赖，OAuth2 邮箱明确提示并提供"应用专用密码"路径 |

### 3.2 邮件协议层修复

- **IMAP 连接**：尊重账户的 `imapSecure`（SSL/STARTTLS/明文），不再无条件启用 SSL；补齐连接/读/写超时与主机名校验；不再改写 `System.getProperties()`。
- **SMTP 验证**：原 SSL 分支只创建对象、从不认证，验证"永远成功"；改为统一的 `Transport.connect(host, port, user, pwd)`。
- **附件识别**：原 `isMimeType("multipart/*")` 会把所有 `multipart/alternative` 正文误判为附件；改为递归遍历 MIME 树，按 `disposition=attachment` 或存在文件名判定。
- **正文解析**：递归提取 `text/plain` / `text/html`，纯文本缺失时由 HTML 反推；正文长度截断（文本 20k / HTML 200k）避免超大邮件拖垮列表。
- **排序**：IMAP 返回升序，列表改为倒序（最新在最上）。
- **资源回收**：连接与 Folder 的关闭放入 `finally`，异常路径也不泄漏。

### 3.3 状态与边界处理

- 新增 `MailUiState`（Idle / Loading / Content / Empty / Error）与 `LoginUiState`（Ready / Submitting / Success / NeedsAppPassword / Error / NavigateToMain），
  空收件箱与收取失败不再呈现同一种界面。
- 错误统一由 `ErrorMessages` 沿 cause 链匹配（认证失败、域名解析、连接拒绝、超时、TLS、网络），给出可执行的提示，
  而不是把 `AuthenticationFailedException` 原文丢给用户。
- 写信：逐字段校验地址（逗号/分号/空白分隔、`张三 <a@b>` 形式均可），错误定位到具体输入框；地址为空/格式错误分别提示；发送成功才返回 `RESULT_OK`；
  编辑过内容后返回会二次确认，避免误丢草稿。
- 登录：字段级校验；SMTP 账户同时验证收/发两条通道；账户落库失败单独兜底；防重复提交。
- 设置：删除账户二次确认 + 失败提示；删除当前正在浏览的账户时主界面自动回到账户列表。
- 详情：HTML 主文档加载失败自动降级为纯文本并提示；链接外跳失败不崩溃；时间缺失显示"时间未知"。
- 配置变更（旋转/切换深浅色）后由 ViewModel 保留当前账户，状态驱动渲染自动恢复原位。

### 3.4 主动收敛的无效入口

- 移除详情页的"删除/归档"菜单（IMAP 以只读方式打开，功能本身不存在，点击无反应）；
- 移除未使用的 `GET_ACCOUNTS` 权限；
- 移除不会被 inflate 的 `onCreateOptionsMenu`（与工具栏 `app:menu` 重复）。

---

## 四、验证方式与已知限制

### 4.1 构建验证（真实执行）

环境：JDK 17.0.20 + Gradle 8.2 + AGP 8.2.0 + Android SDK Platform 34 / Build-Tools 34.0.0。

```bash
# 本仓库的 gradlew 是自定义脚本，需要先指定 SDK
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleDebug     # BUILD SUCCESSFUL
./gradlew :app:lintDebug         # BUILD SUCCESSFUL，0 error
```

结果：`app/build/outputs/apk/debug/app-debug.apk`（约 6.4 MB），lint **0 error / 19 warning**
（warning 全部为依赖版本提示、启动图 PNG 形状、可选 KSP 迁移等非阻断项）。

编译过程中额外发现并修复的问题（静态检查覆盖不到的部分）：

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| `AAPT: resource style/ShapeAppearance.ZYEmail not found` | 以点号命名的样式会被隐式推导父样式 | 为三个 ShapeAppearance 补 `parent=""` |
| `Unclosed comment` | Kotlin 支持嵌套块注释，注释中写了 `multipart/*` | 文档注释改写，避免 `/*` 序列 |
| `Unresolved reference: messageID` | `getMessageID()` 属于 `MimeMessage`，不在 `Message` 接口上 | 转为 `MimeMessage` 后读取 |
| `setBackgroundColor(String)` 类型不匹配 | WebView 需要 int 颜色，误传了 CSS 字符串 | 改用 `ContextCompat.getColor` |
| `Unresolved reference: BuildConfig` | 该工程未开启 buildConfig 生成 | 改用 `PackageManager` 读取版本号 |
| `META-INF/NOTICE.md` 打包冲突 | `android-mail` 与 `android-activation` 同名许可文件 | `packaging.resources.excludes` 排除 |
| 自定义 `gradlew` 依赖 `ANDROID_HOME` | 仓库内 `gradlew` 不是官方 wrapper 脚本 | 按脚本要求设置环境变量（已在上面命令中说明） |

### 4.2 持续集成

`.github/workflows/build-apk.yml` 在 push / PR / 标签 / 手动触发时自动构建：

| Job | 内容 | 产物 |
| --- | --- | --- |
| `build-debug` | JDK 17 + Gradle 8.2（setup-gradle 缓存）+ 补齐 SDK 34 → `assembleDebug` | `debug-apk`（约 5.5 MB） |
| `lint` | `lintDebug`，输出错误/提示统计到 Step Summary | `lint-results`（html/xml/txt） |
| `build-release` | 仅 `v*` 标签或手动触发，产出未签名 release 包 | `release-apk` |

设计要点：

- 使用 `gradle/actions/setup-gradle` 提供与本地一致的 Gradle 8.2 并缓存依赖，避免仓库内自定义 `gradlew` 每次下载 120MB 发行包；
- 显式 `sdkmanager` 补齐 `platforms;android-34` 与 `build-tools;34.0.0`，不依赖 runner 镜像的版本漂移；
- 并发分组 `concurrency` 自动取消同分支的旧构建；
- 修正了原工作流的 lint 报告路径（AGP 8 输出为 `lint-results-debug.html`，原路径 `lint-results.html` 永远取不到文件）；
- release job 加了触发条件，避免每次 PR 都跑（原来每次都会执行）。

首次运行结果：`build-debug` 与 `lint` 均通过，产物 `debug-apk` 5.47 MB、`lint-results` 26 KB。

### 4.3 静态校验

1. `res/` 全部 XML 通过解析校验（并修正了注释中出现 `--` 导致 AAPT 报错的写法）；
2. 资源引用完整性校验：布局/主题/清单中所有 `@color|@dimen|@string|@drawable|@style|@bool|@menu` 均可在项目中解析；
3. ViewBinding 一致性校验：Kotlin 中每个 `binding.xxx` 都能在对应布局中找到 `@+id/xxx`；每个 `R.string|color|drawable|id` 均已定义；
4. Material 1.11.0 资源核对：所用 M3 主题、控件样式、attr 与类名均存在于该版本；
5. 配色对比度按 WCAG 2.1 公式实测（见 1.4）；
6. 编译期验证：Kotlin 编译、kapt（Room）、资源链接、dex 合并、APK 打包全链路通过。

**已知限制**

- `EncryptionUtils` 仍使用硬编码主密钥，且加解密失败时回退明文；建议后续接入 Android Keystore（会涉及存量数据迁移，本次未改动以保持兼容）。
- OAuth2 交互式授权需要 Azure / Google 的应用注册信息（clientId、重定向 URI），当前版本未内置，因此 Outlook / Gmail 请使用"应用专用密码"登录。
- 邮件正文按需一次性拉取（最多 50 封），尚未实现分页与懒加载；大附件未做下载管理。
- 未实现已读回写、删除/归档等 IMAP 写操作（本次仅移除了无效入口，未新增服务端写能力）。
