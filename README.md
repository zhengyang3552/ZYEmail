# ZYEmail - Android 邮箱客户端

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
- ✅ Microsoft Outlook
- ✅ Hotmail
- ✅ Live
- ✅ Gmail
- ✅ Google 邮箱

**配置方式：** 通过浏览器进行 OAuth2 授权登录

### 核心功能
- 📧 多账户管理
- 📥 IMAP 邮件收取
- 📤 SMTP 邮件发送
- 🔄 邮件下拉刷新
- 📝 邮件撰写（支持抄送、密送）
- 📖 邮件详情查看（支持 HTML 邮件）
- 🔐 密码加密存储
- ⚙️ 账户设置管理

## 技术栈

- **语言：** Kotlin
- **架构：** MVVM + Repository
- **UI：** Material Design 3
- **数据库：** Room
- **网络：** JavaMail/JavaIMAP (SMTP/IMAP)
- **认证：** MSAL (Microsoft Authentication Library)
- **异步：** Kotlin Coroutines

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
- Android SDK 34

### 构建步骤
1. 克隆仓库
2. 在 Android Studio 中打开项目
3. 等待 Gradle 同步完成
4. 运行或构建 APK

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
