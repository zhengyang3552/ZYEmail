# ZYEmail - Android 邮箱客户端

[![Build APK](https://github.com/zhengyang3552/ZYEmail/actions/workflows/build-apk.yml/badge.svg)](https://github.com/zhengyang3552/ZYEmail/actions/workflows/build-apk.yml)

一个支持 SMTP 和 OAuth2 登录的 Android 邮箱客户端。

## 功能特性

### 支持的邮箱服务

#### SMTP 方式（适用于 QQ、163、126 等邮箱）
- ✅ QQ邮箱
- ✅ 163邮箱
- ✅ 126邮箱
- ✅ 新浪邮箱
- ✅ Yahoo邮箱
- ✅ 企业邮箱
- ✅ 其他支持 SMTP/IMAP 的邮箱

**配置方式：** 邮箱地址 + 密码/授权码

#### OAuth2 方式（适用于 Microsoft、Google 等）
- ⚠️ Microsoft Outlook / Hotmail / Live
- ⚠️ Gmail / Google 邮箱

**配置方式：** OAuth2 交互式授权需要 Azure AD / Google Cloud 的应用注册信息
（clientId、重定向 URI）。当前版本未内置该信息，登录页会提示并引导开启
「使用应用专用密码登录」，改走 SMTP / IMAP 通道（前提是该邮箱已开启应用专用密码）。

### 核心功能
- 📧 多账户管理（添加 / 切换 / 删除）
- 📥 IMAP 邮件收取（倒序、未读识别、附件识别）
- 📤 SMTP 邮件发送（支持抄送、密送、地址校验）
- 🔄 下拉刷新与失败重试
- 📝 邮件撰写 / 回复 / 全部回复 / 转发（引用原文）
- 📖 邮件详情查看（HTML 与纯文本自适应、深色排版）
- 🎨 Material 3 主题（跟随系统 / 浅色 / 深色）
- 🔐 密码加密存储
- ⚙️ 账户与外观设置

## 界面与设计

- **Material 3**：`Theme.Material3.DayNight.NoActionBar`，语义化颜色角色（primary / secondary / tertiary /
  surface container / outline / error / success / warning），深浅两套令牌同名映射。
- **明暗模式**：`values/` 与 `values-night/` 双份令牌，配合 `ThemeManager` 持久化「跟随系统 / 浅色 / 深色」。
- **设计令牌**：4dp 间距栅格、M3 Shape Scale（4/8/12/16/28dp）、M3 Type Scale 九级字阶。
- **对比度**：正文与背景组合均达到 WCAG AA（≥4.5:1）。

配色方案、令牌表、对比度实测与改造清单见：

- [`docs/design-tokens.html`](docs/design-tokens.html) — 可打开的配色/令牌可视化说明
- [`docs/REFACTOR_NOTES.md`](docs/REFACTOR_NOTES.md) — 改造说明与问题修复清单

## 技术栈

- **语言：** Kotlin
- **架构：** MVVM + Repository
- **UI：** Material Design 3（Material Components 1.11.0，View 体系 + ViewBinding）
- **数据库：** Room
- **网络：** JavaMail/JavaIMAP (SMTP/IMAP)
- **异步：** Kotlin Coroutines（网络与磁盘操作统一走 Dispatchers.IO）

## 项目结构

```
ZYEmail/
├── app/
│   ├── src/main/java/com/zy/email/
│   │   ├── data/
│   │   │   ├── database/     # Room数据库
│   │   │   ├── model/        # 数据模型
│   │   │   └── repository/   # 数据仓库
│   │   ├── ui/
│   │   │   ├── activity/     # 活动页面
│   │   │   ├── adapter/      # 适配器
│   │   │   └── viewmodel/    # ViewModel
│   │   ├── utils/            # 工具类
│   │   └── EmailApp.kt       # 应用入口
│   └── src/main/res/         # 资源文件
├── .github/workflows/        # CI/CD
└── gradle/                   # Gradle配置
```

## 快速开始

### 环境要求
- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34（compileSdk 34 / build-tools 34.0.0，minSdk 24）

### 构建步骤
1. 克隆仓库
2. 在 Android Studio 中打开项目
3. 等待 Gradle 同步完成
4. 运行或构建 APK

命令行构建（仓库内的 `gradlew` 是自定义脚本，会先检查 `ANDROID_HOME`）：

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleDebug      # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:lintDebug          # 代码检查
```

## CI/CD

本项目使用 GitHub Actions 进行持续集成：

- **lint** - 代码检查
- **build-debug** - 构建 Debug APK
- **build-release** - 构建 Release APK

每次推送代码到 main/master 分支都会自动触发构建。

## 注意事项

### QQ邮箱
需要在 QQ邮箱设置中开启 SMTP 服务，并使用**授权码**而非登录密码。

### 163邮箱
需要在 163邮箱设置中开启 SMTP 服务，并使用**授权码**。

### Microsoft邮箱
需要配置 Azure AD 应用，获取 Client ID，并在 OAuth2Manager 中配置。

### Google/Gmail
需要配置 Google Cloud Console 应用，获取 Client ID 和 Client Secret。

## 许可证

MIT License

## 作者

ZYEmail Team
