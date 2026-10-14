# 单词速记（WordFlash）

一款基于艾宾浩斯遗忘曲线的 Android 背单词应用。**数据全部本地存储，不联网。**

## 功能

| 模块 | 说明 |
|---|---|
| **词库** | 内置四六级 / 考研 / 托福 4 个示例词库（各 40 词） |
| **自建词库** | 三种录入方式：① 逐条手打 ② 粘贴一段文本自动解析 ③ 从 CSV/TXT/JSON 文件导入 |
| **学习模式** | 单词卡片（翻转+发音）、选词填空、拼写默写、听音辨词 |
| **记忆算法** | SM-2 简化变体，间隔 1→2→4→7→15→30→60→180 天，含难度系数自适应 |
| **错题本** | 答错自动收录（带错误次数）；答对自动移出；支持「专项练习」只练错题 |
| **生词本 / 收藏** | 手动收录，与错题本职责分离；支持搜索 |
| **数据统计** | 打卡天数、学习时长、正确率趋势、每日明细、未来 7 天复习量预测 |
| **设置** | 深/浅色主题、动态取色、发音语速、每组词量、清缓存、清记录 |

### 错题本 vs 生词本

两者**职责不同，不会重复**：

| | 错题本 | 生词本 |
|---|---|---|
| 来源 | **系统自动**（答错即收录） | **用户手动**（点按钮添加） |
| 移出 | 答对自动移出，或手动删除 | 手动删除 |
| 额外信息 | 显示累计错误次数 | 可写笔记 |

设计上错题本用 `word_progress.is_in_wrong_book` 标志位，
而**不是**用 `wrong_count > 0` 判断 —— 因为 `wrong_count` 是只增不减的
统计值（改了会让正确率失真），而错题本的成员资格需要能被"答对"清除。


## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository（单向数据流） |
| 数据库 | Room 2.6.1（6 张表） |
| 设置存储 | DataStore Preferences |
| 依赖注入 | Hilt 2.52 |
| 导航 | Navigation Compose 2.8.5 |
| 发音 | Android 原生 TextToSpeech |
| 图表 | Compose Canvas 自绘（无第三方库） |
| 最低版本 | minSdk 24（Android 7.0）/ targetSdk 35 |

## 项目结构

```
app/src/main/java/com/wordflash/app/
├── WordFlashApp.kt              应用入口（词库播种 + 数据清理）
├── MainActivity.kt              单 Activity，主题在此生效
├── data/
│   ├── local/                   Room：6 实体 + 4 DAO + 数据库
│   │   ├── entity/              WordBook, Word, WordProgress,
│   │   │                        StudyRecord, DailyStats, WordMark
│   │   └── dao/                 WordBookDao, ProgressDao, StatsDao, MarkDao
│   ├── repository/              WordRepository, StudyRepository,
│   │                            StatsRepository, SettingsRepository
│   └── seed/                    DictionarySeeder（assets 词库导入）
├── domain/
│   ├── model/                   StudyMode, StudyCard, SessionConfig,
│   │                            SessionSummary, WordWithProgress
│   ├── scheduler/               EbbinghausScheduler  ★ 算法核心
│   └── session/                 StudySessionManager  ★ 会话状态机
├── di/                          Hilt 模块（Database / Repository / TTS）
├── tts/                         TtsManager（发音封装）
├── util/                        DateUtils, WordImporter
└── ui/
    ├── theme/                   配色、字体、主题
    ├── navigation/              路由表 + 导航图
    ├── components/              进度条、空状态、统计片、环形图
    └── screen/                  home / booklist / study / result
                                 stats / wordbook / settings / importbook
```

## 快速开始

### 1. 环境

- **Android Studio**（2024.2 或更新）
- **JDK 17**（Android Studio 自带即可）
- **Android SDK**：API 35 + Build-Tools 35

### 2. 打开项目

Android Studio → `File` → `Open` → 选择本项目根目录。

首次会提示 **Gradle Wrapper 缺失**，点 `OK` 让它自动补齐。

### 3. 构建

```powershell
# 图形界面：Build → Make Project (Ctrl+F9)
# 或命令行：
.\gradlew.bat assembleDebug
```

### 4. 一键打包脚本

```powershell
.\build-apk.ps1              # debug APK
.\build-apk.ps1 -Release     # release APK
.\build-apk.ps1 -Bundle      # AAB
.\build-apk.ps1 -GenKeystore # 生成签名密钥
```

> 若报「未对文件进行数字签名」，先执行：
> `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`

## 文档

| 文档 | 内容 |
|---|---|
| [P2-数据层](docs/P2-数据层.md) | 表结构设计、索引、词库导入格式 |
| [P3-记忆算法](docs/P3-记忆算法.md) | SM-2 变体原理、**78 项测试结果** |
| [P5-统计与设置](docs/P5-统计与设置.md) | 图表实现、缓存边界、导入流程 |
| [P6-打包APK](docs/P6-打包APK.md) | **签名、打包、常见错误排查** |

## 已知限制

**词库是示例包，不是完整词库。**

每个词库 40 词（音标、释义、例句均为真实内容），足以跑通全部功能。
完整的四六级/考研/雅思词表属于商业版权资产，不能随应用分发。
需要完整词库时，用「导入自定义词库」导入你自备的 CSV/JSON——
格式说明见 [P2-数据层](docs/P2-数据层.md)。

## 导入格式速查

**CSV / TXT**（首行表头可省略，列顺序可任意）：

```csv
spelling,phonetic,pos,meaning,example,exampleTranslation
abandon,/əˈbændən/,v.,放弃；抛弃,He abandoned his car in the snow.,他把车丢弃在雪地里。
```

只有 **拼写** 和 **释义** 必填。分隔符自动识别逗号或制表符
（从 Excel 直接复制的 TSV 也能导入）。

**JSON**：与 `assets/dict/*.json` 同构，或纯单词数组。

## 测试

算法部分有可执行的验证程序（纯 Java，不需要 Android 环境）：

```powershell
cd tools/verify
& "D:\Develop\Java\bin\javac.exe" -encoding UTF-8 -d . SchedulerCheck.java SessionCheck.java
& "D:\Develop\Java\bin\java.exe" -Dfile.encoding=UTF-8 -cp . SchedulerCheck
& "D:\Develop\Java\bin\java.exe" -Dfile.encoding=UTF-8 -cp . SessionCheck
```

**预期：50 + 28 = 78 项断言全部通过。**

## 许可

本项目代码可自由使用与修改。

`assets/dict/` 下的示例词库数据为自建内容，词条释义与例句为通用表述，
音标采用标准 IPA。如需商用请自行核实数据来源。
