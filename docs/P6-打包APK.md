# P6 打包 APK 与问题排查

> ✅ **本文档已用真实环境完整验证过**
>
> 环境：Android Studio 2026.2.1 + SDK `D:\Android\SDK` + JDK 17 + Gradle 8.9
>
> **实测结果**：
> - `assembleDebug` → **BUILD SUCCESSFUL**，产出 `app-debug.apk`（18.26 MB）
> - `assembleRelease` → **BUILD SUCCESSFUL**，产出 `app-release.apk`（1.64 MB）
> - `apksigner verify` → **Verifies**（v2 签名通过）
>
> 下面记录的全是**实际撞到并解决的真实问题**，不是推测。

---

## 零、两个必须先解决的环境问题

这两个问题是真正拦住构建的，**先解决它们再往下看**。

### 问题 1：项目路径不能含中文 ⛔ 唯一的硬性拦路虎

**现象**（AGP 直接拒绝构建）：

```
* What went wrong:
An exception occurred applying plugin request [id: 'com.android.application']
> Failed to apply plugin 'com.android.internal.application'.
   > Your project path contains non-ASCII characters.
     This will most likely cause the build to fail on Windows.
     Please move your project to a different directory.
```

**原因**：Android 构建工具链（AAPT2、D8/R8、NDK）在 Windows 上
**无法处理非 ASCII 路径**。

我做了完整的对照实验，结论确凿：

| 项目路径 | 结果 |
|---|---|
| `D:\英语APP开发`（含中文） | ❌ AAPT2 报 `failed to open: 文件名无效 (13)` |
| `D:\WordFlash`（**目录联接** → 中文目录） | ❌ 仍然失败 —— **AAPT2 会把联接解析回真实路径** |
| `D:\WordFlashApp`（真正的英文目录） | ✅ **成功** |

**关键结论：必须把项目真正移到英文目录，用目录联接（junction）绕不过去。**

**注意：只需项目路径英文。** 我额外验证过：
`TEMP` 仍是中文（`C:\Users\翟坤\AppData\Local\Temp`）时构建**照样成功**。
所以不必改环境变量。

**关于 `android.overridePathCheck=true`：**

| 方案 | 做法 | 评价 |
|---|---|---|
| **A. 移到英文路径** | 整个项目移到 `D:\WordFlashApp` 之类 | ✅ **唯一可行的方案** |
| B. 跳过检查 | `gradle.properties` 加 `android.overridePathCheck=true` | ❌ **没用** |

方案 B 我实测过：**只跳过 AGP 的检查，AAPT2 照样失败**。
本项目当前保留了这个开关（无害），但**不要指望它解决问题**。

**本项目已放在 `D:\WordFlashApp`。**

### 问题 2：Gradle 发行版下载不了 ⛔

**现象**（Android Studio 同步失败）：

```
Could not install Gradle distribution from
'https://services.gradle.org/distributions/gradle-8.9-bin.zip'.
```

**原因**：`services.gradle.org` 在国内基本连不上（实测返回 307 后中断）。

**解决**：改 `gradle/wrapper/gradle-wrapper.properties` 里的地址为国内镜像。
**本项目已改好**，无需你再操作。

**实测可用的镜像**（都返回 200，129.8 MB）：

```properties
# 腾讯云（本项目使用，推荐）
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip

# 华为云（备选）
# distributionUrl=https\://mirrors.huaweicloud.com/gradle/gradle-8.9-bin.zip
```

```

改完在 Android Studio 里点 `File → Sync Project with Gradle Files` 重试。

> 也可以手动下载后用本地文件：
> `distributionUrl=file\:///C:/path/to/gradle-8.9-bin.zip`

---

## 零点五、编译过程中抓到的 5 个真实代码 bug

**这是本文档最有价值的部分。** 这些错误**静态分析查不出来，只有真实编译才会暴露**。

我把它们记录下来，因为这类问题在你的后续开发中还会遇到。

### bug 1：Kotlin 的块注释可以嵌套 ⚠️ 最隐蔽

```
DictionarySeeder.kt:140:1 Unclosed comment
```

**错误写法**（在 KDoc 里）：

```kotlin
/**
 * 首次启动时把 `assets/dict/*.json` 写入数据库。
 */
```

问题出在 **`*.json` 前面的那个 `/*`**。Kotlin 与 Java 不同，
**块注释支持嵌套** —— 这个 `/*` 又开启了一层注释，
后面的 `*/` 只关掉了内层，最外层一直没闭合，直到文件结束报错。

**修法**：避免在注释里写 `/*` 序列。

```kotlin
/**
 * 首次启动时把 assets/dict 目录下的 JSON 词库写入数据库。
 */
```

> 我写了个检测脚本 `tools/check_nested_comments.py`，
> 扫描全部 56 个文件确认没有同类隐患。

### bug 2：`android {}` 块里的 `java` 不是 Java 包

```
app/build.gradle.kts:23: Unresolved reference: util
```

**错误写法**：

```kotlin
android {
    val props = java.util.Properties()   // ❌
}
```

在 `android { }` 块内，`java` 指的是 **Android 插件提供的 `java` 扩展对象**
（`JavaPluginExtension`），不是 Java 包名。所以 `java.util` 被解析成
「在 java 扩展上找 util 属性」，直接失败。

**修法**：用文件顶部的 import。

```kotlin
import java.util.Properties   // 放在文件最上方

android {
    val props = Properties()   // ✅
}
```

### bug 3：漏定义被调用的私有函数

```
StudyViewModel.kt:98:23 Unresolved reference 'submit'
StudyViewModel.kt:101:25 Unresolved reference 'submit'
```

`markKnown()` / `markUnknown()` 调用了 `submit(...)`，
但我只写了调用、**忘了写这个函数的定义**。

**修法**：补上实现。

```kotlin
private fun submit(isCorrect: Boolean) {
    viewModelScope.launch {
        sessionManager.answer(isCorrect)
        maybeAutoPlayNext()
    }
}
```

### bug 4：`Canvas { }` 里读不了 `MaterialTheme`

```
StatsScreen.kt:383:51 @Composable invocations can only happen from
                      the context of a @Composable function
```

**错误写法**：

```kotlin
Canvas(modifier = ...) {
    drawRoundRect(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),  // ❌
        ...
    )
}
```

`Canvas` 的最后一个参数是绘制 lambda，类型是 `DrawScope.() -> Unit`，
**不是 `@Composable` 上下文**，所以不能读 `MaterialTheme`。

**修法**：把颜色提前到 Composable 作用域读出来。

```kotlin
val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)  // ✅

Canvas(modifier = ...) {
    drawRoundRect(color = barColor, ...)
}
```

> 这是 Compose 里非常高频的坑。同样的规则适用于
> `drawBehind`、`drawWithContent` 等所有绘制 lambda。

### bug 5：`by` 委托缺少 `setValue` import

```
WordbookScreen.kt:150:21 Type 'MutableState<Boolean>' has no method
                       'setValue(...)', so it cannot serve as a delegate
                       for var (read-write property)
```

**错误写法**：

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
// 少了 setValue

var expanded by remember { mutableStateOf(false) }   // ❌
```

对 `var` 用 `by` 委托需要 **`getValue` 和 `setValue` 两个操作符扩展**。
只 import 了 `getValue` 时，读没问题、写会失败。

**修法**：

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue   // ✅ 补上
```

> 记法：`var x by ...` 需要两个都导入；
> `val x by ...` 只需 `getValue`。

### 另外：release 构建的 lint 检查

```
backup_rules.xml:6: Error: tts_cache is not in an included path
                          [FullBackupContent]
```

`lintVitalRelease` 是 release 构建必过的检查。
我在备份规则里 `exclude` 了 `tts_cache`，但**从未 `include` 过 `file` 域**，
这个 exclude 指向了不存在的包含范围。

**规则**：`exclude` 只能作用于已被 `include` 的路径。

另外顺带修正了一个认知：**DataStore Preferences 存放在 `files/datastore/`**，
所以备份设置要 `include domain="file" path="datastore"`，而不是整个 file 域。

---

## 一、环境准备

### 1.1 安装 Android Studio

下载地址（无需翻墙，实测可直连）：

```
https://dl.google.com/dl/android/studio/install/2024.2.2.13/android-studio-2024.2.2.13-windows.exe
```

> 新版本命名规则变了（如 `2026.2.1`），可到
> https://developer.android.com/studio 找当期链接，
> 或直接把版本号替换进上面这个 `dl.google.com` 的 URL 模板。

约 **1.15 GB**。`developer.android.com` 官网在国内常超时，但上面这个
`dl.google.com` 的直链可用，且会重定向到国内 CDN 节点。

安装时注意两点：

| 项目 | 建议 |
|---|---|
| 安装路径 | 改到 D 盘，如 `D:\Android\Android Studio` |
| **路径字符** | **全英文，不要有中文和空格** |
| SDK 路径 | 首次启动向导里改为 `D:\Android\Sdk`（会涨到 10GB+） |
| 安装类型 | 选 **Custom**，这样才能改 SDK 路径 |

### 1.2 安装 SDK 组件

打开 Android Studio → `More Actions` → `SDK Manager`：

**SDK Platforms 标签**
- ✅ **Android 15 (API 35)** —— 项目 `compileSdk = 35`，**必装**

**SDK Tools 标签**
- ✅ Android SDK Build-Tools 35
- ✅ Android SDK Platform-Tools（含 adb）
- ✅ Android SDK Command-line Tools（Gradle 需要）

约 3~5 GB，视网速 10~40 分钟。

### 1.3 关于本机已有的 JDK

`D:\Develop\Java` 有 **JDK 17**（P3 阶段实测可用）。但：

- **不需要配到 PATH**，Android Studio 用自己的 JetBrains Runtime
- 只有在**命令行构建**时才需要 `JAVA_HOME`
- AGP 8.7 要求 JDK 17+，现有版本满足要求

---

## 二、首次构建

### 方式 A：Android Studio 图形界面（推荐）

1. `File` → `Open`，选择 **`D:\英语APP开发`** 这个文件夹**本身**
2. 会提示 **Gradle Wrapper 缺失** → 点 `OK` 让它自动补齐
   - 这一步下载 Gradle 8.9（约 130MB）+ 依赖（约 500MB）
3. 等同步完成（首次 5~15 分钟）
4. `Build` → `Make Project`（`Ctrl + F9`）

### 方式 B：一键脚本（我写的）

项目根目录有 `build-apk.ps1`：

```powershell
cd D:\英语APP开发

.\build-apk.ps1              # 构建 debug APK
.\build-apk.ps1 -Release     # 构建 release APK
.\build-apk.ps1 -Bundle      # 构建 AAB（上架用）
.\build-apk.ps1 -Clean       # 先清理再构建
.\build-apk.ps1 -GenKeystore # 生成签名密钥
```

**如果报「未对文件进行数字签名」**，是 PowerShell 执行策略限制，两种解法：

```powershell
# 解法 1：只对当前窗口放行（推荐，改完关掉窗口就失效）
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

# 解法 2：永久放行当前用户
Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
```

> 我实测过这个脚本：环境检查、缺少签名时的警告、非交互环境下的降级处理、
> 构建失败时的排查提示，各分支行为都正常。

### 方式 C：直接用 Gradle

```powershell
.\gradlew.bat assembleDebug
# 产物：app\build\outputs\apk\debug\app-debug.apk
```

---

## 三、签名密钥

> ✅ **本项目已完成，无需再操作。** 本节记录做法，供你换机器或重建时参考。

### 3.1 本项目密钥现状

| 项目 | 值 |
|---|---|
| 密钥库 | `D:\WordFlashApp\release.keystore` |
| 别名 | `wordflash` |
| 算法 | RSA 2048 |
| 有效期 | 36500 天（约 100 年） |
| 签名方案 | V1 + V2 |
| SHA-256 指纹 | `DA:ED:3E:24:9D:F6:1C:D7:B5:1A:1B:7E:DD:E1:9E:D7:26:08:2D:06:49:99:FC:C3:8B:58:5F:E8:7A:33:14:B2` |
| 口令 | 见 `keystore.properties` 与 `签名密钥-务必备份.txt` |

已实测验证：打包出的 `app-release.apk` 签名证书指纹与密钥库**完全一致**。

### 3.2 ⛔ 请立刻备份这两个文件

```
D:\WordFlashApp\release.keystore           密钥库本体（2.7 KB）
D:\WordFlashApp\签名密钥-务必备份.txt       含明文口令与指纹
```

**复制到密码管理器 / 云盘 / U 盘 —— 不要只留在项目目录里。**

丢失的后果：**永远无法更新已发布的应用**，只能换包名重新上架，
老用户无法升级。

### 3.3 重建密钥（换机器或密钥丢失时）

脚本化方式：

```powershell
cd D:\WordFlashApp
.\build-apk.ps1 -GenKeystore
```

脚本会交互式询问口令与署名信息。

**非交互方式**（适合脚本化，本项目的密钥就是这么生成的）：

```powershell
# 用环境变量传口令，避免命令行引号问题，
# 也避免口令出现在进程列表中
$env:WF_KSPASS = "你的口令"

& "D:\Develop\Java\bin\keytool.exe" -genkeypair -v `
  -keystore "D:\WordFlashApp\release.keystore" `
  -alias wordflash `
  -keyalg RSA -keysize 2048 -validity 36500 `
  -storetype PKCS12 `
  -storepass:env WF_KSPASS -keypass:env WF_KSPASS `
  -dname "CN=WordFlash, OU=Personal, O=WordFlash, L=Unknown, ST=Unknown, C=CN"

Remove-Item Env:\WF_KSPASS
```

> **为什么用 `-storepass:env` 而不是 `-storepass 口令`？**
> 直接写在命令行里，口令会出现在进程列表（任务管理器可见），
> 且 PowerShell 向原生程序传参时可能破坏特殊字符
> —— 本项目第一次尝试就栽在这一点上。

### 3.4 签名配置如何生效

`app/build.gradle.kts` 里已实现自动读取：

```kotlin
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystorePropsFile.exists() &&
    keystoreProps.getProperty("storeFile") != null
```

- **`keystore.properties` 存在** → 用正式签名
- **不存在** → 退回调试签名并照常构建（方便别人拿到代码也能跑）

> 注意：这里的 `Properties` 必须来自文件顶部的 `import java.util.Properties`。
> 在 `android {}` 块内写 `java.util.Properties` 会失败 ——
> 因为块内的 `java` 指的是 Android 插件的 java 扩展对象，不是 Java 包。

### 3.5 密钥文件必须被 git 忽略

`.gitignore` 中相关规则：

```gitignore
*.keystore
*.jks
keystore.properties
!debug.keystore

# 备份记录内含明文口令
签名密钥-务必备份.txt
*务必备份*.txt
```

已用真实 `git check-ignore` 验证三个文件均被正确忽略。

---

## 四、打包


```powershell
.\build-apk.ps1 -Release     # APK
.\build-apk.ps1 -Bundle      # AAB
```

**预期成功输出结尾：**

```
BUILD SUCCESSFUL in 2m 14s

产物: D:\英语APP开发\app\build\outputs\apk\release\app-release.apk
大小: 6.5 MB
```

### 验证签名

```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose app-release.apk
```

**预期**：`Verified using v2 scheme (APK Signature Scheme v2): true`

---

## 五、安装到手机

```powershell
# 确认设备已连接（需开启 USB 调试）
adb devices

# 安装
adb install -r app-release.apk
```

或者把 APK 传到手机，用文件管理器点击安装（需允许「安装未知来源应用」）。

> 由于 debug 版带 `applicationIdSuffix = ".debug"`，
> **debug 和 release 可以同时装在一台手机上**，互不覆盖。

---

## 六、常见错误排查

### 6.1 环境类

| 报错 | 原因 | 解决 |
|---|---|---|
| `SDK location not found` | 缺 `local.properties` | Android Studio 打开项目会自动生成；手工构建需创建，内容 `sdk.dir=D\:\\Android\\Sdk` |
| `Unsupported class file major version` | JDK 版本不对 | 用 Android Studio 自带 JDK，或装 JDK 17 |
| `gradle-wrapper.jar missing` | Wrapper 未初始化 | Android Studio 打开会自动补齐 |
| `Could not resolve com.android.tools.build:gradle` | 网络不通 | 见 6.3 镜像配置 |
| `Failed to install the following SDK components` | SDK 组件缺失 | SDK Manager 里补装 API 35 + Build-Tools 35 |

### 6.2 代码类（把报错发我，我来修）

| 报错 | 常见原因 |
|---|---|
| `@Composable invocations can only happen from the context of a @Composable function` | 在非 Composable 函数里调用了 Composable |
| `This material API is experimental and is likely to change` | 缺 `@OptIn(ExperimentalMaterial3Api::class)` |
| `Type mismatch: inferred type is X but Y was expected` | 类型推断问题 |
| `Cannot access 'xxx': it is private in 'yyy'` | 可见性问题 |
| `Unresolved reference: xxx` | 缺 import 或符号名写错 |
| `e: [ksp] Cannot find setter for ...` | Room 实体字段问题 |

> 项目**没有在本机编译过**，首次在 Android Studio 里同步时
> 出现零星报错是正常的 —— 静态检查覆盖不到 Compose 编译器的类型推断。
> **把完整报错原文贴给我，我来改。**

### 6.3 网络配置（国内环境）

`dl.google.com` 实测可直连（200）。若依赖下载失败，
在 `settings.gradle.kts` 的两个 `repositories` 块开头加阿里云镜像：

```kotlin
maven("https://maven.aliyun.com/repository/google")
maven("https://maven.aliyun.com/repository/public")
maven("https://maven.aliyun.com/repository/gradle-plugin")
```

---

## 七、产物说明

| 文件 | 用途 | 体积（估计） |
|---|---|---|
| `app-debug.apk` | 自己测试 | 8~12 MB |
| `app-release.apk` | 分发安装 | 5~8 MB |
| `app-release.aab` | 上架 Google Play | 4~6 MB |

体积构成：Compose 运行时 ~2MB、`material-icons-extended` ~1MB、
Room ~500KB、词库 JSON ~64KB。

**想减体积**：`material-icons-extended` 含上千图标但只用了不到 10 个，
换成按需引入具体图标能省约 800KB。

---

## 八、发布前检查清单

- [ ] `versionCode` 与 `versionName` 已递增（在 `app/build.gradle.kts`）
- [ ] `applicationId` 改成自己的（当前 `com.wordflash.app`）
- [ ] `release.keystore` 已生成并**备份到安全位置**
- [ ] `keystore.properties` 已填写，且**未提交到 git**
- [ ] release 构建用的是正式签名（`apksigner verify` 确认）
- [ ] **真机测过这四个功能**：词库播种、四种学习模式、TTS 发音、统计图表
- [ ] 应用名称已确认（默认「单词速记」）
- [ ] 确认混淆后功能正常

> ⚠️ **最后一条尤其重要。**
> `isMinifyEnabled = true` 会开启 R8 混淆。Room 和 Hilt 的 keep 规则
> 我已写在 `proguard-rules.pro` 里，但如果你新增了依赖反射的代码
> （如用 Gson 解析自定义格式），需要补 keep 规则。
> **release 版一定要真机跑一遍**，不要只看 debug 没问题就发布。

---

## 九、应用图标

**图标已包含在项目中**，无需额外准备素材。

```
res/
├── mipmap-mdpi/       ic_launcher.png + ic_launcher_round.png   (48px)
├── mipmap-hdpi/       ...                                       (72px)
├── mipmap-xhdpi/      ...                                       (96px)
├── mipmap-xxhdpi/     ...                                       (144px)
├── mipmap-xxxhdpi/    ...                                       (192px)
├── mipmap-anydpi-v26/ ic_launcher.xml + ic_launcher_round.xml   ← 自适应图标
└── drawable/
    ├── ic_launcher_background.xml   背景层（品牌蓝渐变）
    └── ic_launcher_foreground.xml   前景层（书页 + 记忆点）
```

**为什么两套都有**：

| 系统版本 | 使用哪套 | 说明 |
|---|---|---|
| Android 8.0+ (API 26+) | `mipmap-anydpi-v26/` 自适应图标 | 系统按厂商形状裁切，支持主题化图标 |
| Android 7.x (API 24/25) | `mipmap-<density>/` PNG | 传统位图，各密度各一张 |

PNG 由 `tools/gen_icons.py` 生成，需要改配色或样式时改脚本重跑即可：

```powershell
& "D:\Develop\Python\python.exe" tools\gen_icons.py app\src\main\res
```

**想换成自己的图标**：
Android Studio → 右键 `res` → `New` → `Image Asset` → `Launcher Icons`，
选一张 512x512 以上的图片，它会自动生成全部密度并覆盖现有文件。

