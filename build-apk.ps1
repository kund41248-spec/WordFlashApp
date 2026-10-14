# ============================================================
# 单词速记 —— 一键打包脚本
#
# 用法（在项目根目录执行）：
#   .\build-apk.ps1                 # 构建 debug APK
#   .\build-apk.ps1 -Release        # 构建 release APK（需要 keystore.properties）
#   .\build-apk.ps1 -Bundle         # 构建 AAB（上架 Google Play 用）
#   .\build-apk.ps1 -Clean          # 先清理再构建
#   .\build-apk.ps1 -GenKeystore    # 生成新的签名密钥
#
# 注意：本脚本需要先装好 Android Studio 与 SDK。
# ============================================================

param(
    [switch]$Release,
    [switch]$Bundle,
    [switch]$Clean,
    [switch]$GenKeystore
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

function Info($msg)  { Write-Host $msg -ForegroundColor Cyan }
function Ok($msg)    { Write-Host $msg -ForegroundColor Green }
function Warn($msg)  { Write-Host $msg -ForegroundColor Yellow }
function Fail($msg)  { Write-Host $msg -ForegroundColor Red; exit 1 }

# Read-Host 在非交互环境（CI、管道输入）会抛异常。
# 包一层：拿不到输入时返回默认值，避免脚本莫名中断。
function Confirm($prompt, $default = $false) {
    try {
        $ans = Read-Host $prompt
        return ($ans -eq 'y' -or $ans -eq 'Y')
    } catch {
        $word = if ($default) { "继续" } else { "取消" }
        Warn "（非交互环境，按默认值处理：$word）"
        return $default
    }
}

# ---------------------------------------------------------------
# 0. 生成签名密钥
# ---------------------------------------------------------------
if ($GenKeystore) {
    Info "=== 生成签名密钥 ==="

    # 按顺序尝试：JAVA_HOME → PATH → Android Studio 自带 JDK
    $keytool = $null
    if ($env:JAVA_HOME) {
        $p = Join-Path $env:JAVA_HOME "bin\keytool.exe"
        if (Test-Path $p) { $keytool = $p }
    }
    if (-not $keytool) {
        $cmd = Get-Command keytool -ErrorAction SilentlyContinue
        if ($cmd) { $keytool = $cmd.Source }
    }
    if (-not $keytool) {
        # Android Studio 各版本的 jbr 目录
        foreach ($base in @(
            "$env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe",
            "${env:ProgramFiles(x86)}\Android\Android Studio\jbr\bin\keytool.exe",
            "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe"
        )) {
            if ($base -and (Test-Path $base)) { $keytool = $base; break }
        }
    }
    if (-not $keytool) {
        Fail @"
找不到 keytool。请先安装 JDK 17，然后二选一：
  1. 设置环境变量 JAVA_HOME 指向 JDK 安装目录
  2. 把 JDK 的 bin 目录加入 PATH
Android Studio 自带的 JDK 也会被自动识别。
"@
    }

    $ksPath = Join-Path $root "release.keystore"
    if (Test-Path $ksPath) {
        Warn "已存在 $ksPath"
        if (-not (Confirm "覆盖会丢失旧密钥，且无法再更新已发布应用。确认覆盖？(y/N)" $false)) {
            Info "已取消"; exit 0
        }
        Remove-Item $ksPath -Force
    }

    Info "将创建：$ksPath"
    Info "有效期 100 年（Google Play 要求至少到 2033 年）"
    Warn "口令务必记牢并备份 —— 丢失后无法更新已发布的应用！"
    Write-Host ""

    & $keytool -genkeypair -v `
        -keystore $ksPath `
        -alias wordflash `
        -keyalg RSA `
        -keysize 2048 `
        -validity 36500 `
        -storetype PKCS12

    if ($LASTEXITCODE -ne 0) { Fail "keytool 执行失败" }

    Ok ""
    Ok "密钥已生成：$ksPath"
    Ok ""
    Ok "接下来请创建 keystore.properties（可从 keystore.properties.example 复制），填入刚才的口令。"
    exit 0
}

# ---------------------------------------------------------------
# 1. 环境检查
# ---------------------------------------------------------------
Info "=== 检查构建环境 ==="

$gradlew = Join-Path $root "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    Fail @"
找不到 gradlew.bat。

这通常意味着 Gradle Wrapper 未被初始化。解决方法：
  1. 用 Android Studio 打开本项目，它会自动补齐
  2. 或安装 Gradle 后执行：gradle wrapper --gradle-version 8.9
"@
}
Ok "  gradlew.bat 存在"

# 检查 local.properties（SDK 路径）
$localProps = Join-Path $root "local.properties"
if (-not (Test-Path $localProps)) {
    Warn "  缺少 local.properties（SDK 路径配置）"
    Warn "  Android Studio 首次打开项目时会自动生成；"
    Warn "  若手工构建失败报 'SDK location not found'，请手动创建它。"
} else {
    Ok "  local.properties 存在"
}

# release 构建检查签名配置
if ($Release -or $Bundle) {
    $ksProps = Join-Path $root "keystore.properties"
    if (-not (Test-Path $ksProps)) {
        Warn ""
        Warn "  未找到 keystore.properties —— release 将使用调试签名，"
        Warn "  该产物无法上架，也无法覆盖安装正式版。"
        Warn "  需要正式签名请先运行：.\build-apk.ps1 -GenKeystore"
        Warn ""
        if (-not (Confirm "仍要继续？(y/N)" $false)) { exit 0 }
    } else {
        Ok "  keystore.properties 存在"
    }
}

# ---------------------------------------------------------------
# 2. 执行构建
# ---------------------------------------------------------------
$task = if ($Bundle) { "bundleRelease" }
        elseif ($Release) { "assembleRelease" }
        else { "assembleDebug" }

if ($Clean) {
    Info ""
    Info "=== 清理 ==="
    & $gradlew clean --console=plain
    if ($LASTEXITCODE -ne 0) { Fail "clean 失败" }
    Ok "  清理完成"
}

Info ""
Info "=== 开始构建：$task ==="
Info "（首次构建需下载 Gradle 与依赖，可能耗时 5~15 分钟）"
Info ""

& $gradlew $task --console=plain --stacktrace
$exit = $LASTEXITCODE

Info ""
if ($exit -ne 0) {
    Write-Host "================ 构建失败 ================" -ForegroundColor Red
    Write-Host ""
    Write-Host "常见原因排查：" -ForegroundColor Yellow
    Write-Host "  SDK location not found      -> 创建 local.properties 指定 sdk.dir"
    Write-Host "  Unsupported class file ...  -> JDK 版本不对，需要 JDK 17"
    Write-Host "  Could not resolve ...       -> 网络问题，见 docs/P6-打包APK.md 的镜像配置"
    Write-Host "  Unresolved reference: xxx   -> 代码问题，把完整报错发给开发者"
    Write-Host ""
    Write-Host "完整排查手册：docs/P6-打包APK.md" -ForegroundColor Cyan
    exit $exit
}

# ---------------------------------------------------------------
# 3. 定位产物
# ---------------------------------------------------------------
Info ""
Ok "================ 构建成功 ================"
Info ""

$outputs = @()
if ($Bundle) {
    $outputs += Get-ChildItem (Join-Path $root "app\build\outputs\bundle") -Recurse -Filter *.aab -ErrorAction SilentlyContinue
} else {
    $dir = if ($Release) { "release" } else { "debug" }
    $outputs += Get-ChildItem (Join-Path $root "app\build\outputs\apk\$dir") -Filter *.apk -ErrorAction SilentlyContinue
}

if ($outputs.Count -eq 0) {
    Warn "构建成功但未找到产物，请检查 app\build\outputs\ 目录"
    exit 0
}

foreach ($f in $outputs) {
    $sizeMB = [math]::Round($f.Length / 1MB, 2)
    Ok ("产物: " + $f.FullName)
    Ok ("大小: $sizeMB MB")
    Ok ("时间: " + $f.LastWriteTime)
    Info ""
}

Info "安装到手机："
Info ("  adb install -r `"" + $outputs[0].FullName + "`"")
Info ""
