# -*- coding: utf-8 -*-
"""
项目静态检查（不需要 Android SDK）。

检查项：
  1. Kotlin 文件的 package 声明与目录路径是否一致
  2. 包内 import 交叉引用是否都能解析
  3. 项目内引用的符号是否都有定义
  4. XML 是否合法（含 aapt 命名空间检查）
  5. Gradle 脚本括号是否配对

用法：
  python tools/check_project.py <项目根目录>
"""
import os
import re
import sys
import glob
import xml.etree.ElementTree as ET

# Compose 的 by 委托必需、但正文不出现标识符的 import，属正常
DELEGATE_IMPORTS = {
    "androidx.compose.runtime.getValue",
    "androidx.compose.runtime.setValue",
}

# 需要确认存在的项目内关键类型
KEY_SYMBOLS = [
    "StudyCard", "StudyMode", "SessionConfig", "SessionSummary",
    "WordWithProgress", "EbbinghausScheduler", "StudySessionManager",
    "TtsManager", "WordImporter", "DateUtils", "BookCategory", "WordState",
    "CorrectGreen", "IncorrectRed", "WarningAmber",
    "AccuracyRing", "StatChip", "EmptyState", "StudyProgressBar", "OptionButton",
]

passed = 0
failed = 0


def ok(msg):
    global passed
    passed += 1
    print(f"  PASS  {msg}")


def bad(msg):
    global failed
    failed += 1
    print(f"  FAIL  {msg}")


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def main():
    root = sys.argv[1]
    src = os.path.join(root, "app", "src", "main", "java")
    res = os.path.join(root, "app", "src", "main", "res")

    kt_files = {}
    for dp, _, fns in os.walk(src):
        for fn in fns:
            if fn.endswith(".kt"):
                p = os.path.join(dp, fn)
                kt_files[p] = read(p)

    print(f"扫描到 {len(kt_files)} 个 Kotlin 文件\n")

    # ---------------------------------------------------------------
    # 1. package 声明 vs 路径
    # ---------------------------------------------------------------
    print("[1] package 声明与目录路径")
    mismatched = 0
    for p, txt in sorted(kt_files.items()):
        rel = os.path.relpath(p, root).replace("\\", "/")
        expected = rel.replace("app/src/main/java/", "")
        expected = expected.rsplit("/", 1)[0].replace("/", ".")
        m = re.search(r"^package\s+([\w.]+)", txt, re.M)
        actual = m.group(1) if m else "(缺失)"
        if actual != expected:
            bad(f"{os.path.basename(p)}: 声明 {actual}，应为 {expected}")
            mismatched += 1
    if mismatched == 0:
        ok(f"全部 {len(kt_files)} 个文件匹配")

    # ---------------------------------------------------------------
    # 2. 收集符号 + import 交叉引用
    # ---------------------------------------------------------------
    print("\n[2] import 交叉引用")
    declared = {}
    for p, txt in kt_files.items():
        m = re.search(r"^package\s+([\w.]+)", txt, re.M)
        pkg = m.group(1) if m else "?"
        patterns = [
            r"^(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+|internal\s+|private\s+|open\s+|abstract\s+|data\s+|sealed\s+|enum\s+)*(?:class|interface|object)\s+(\w+)",
            r"^(?:internal\s+|private\s+)?fun\s+(\w+)",
            r"^(?:internal\s+|private\s+)?(?:const\s+)?(?:val|var)\s+(\w+)",
            r"^(?:internal\s+|private\s+)?typealias\s+(\w+)",
            r"^\s+(?:const\s+)?val\s+(\w+)",
            r"^\s+fun\s+(\w+)",
        ]
        for pat in patterns:
            for name in re.findall(pat, txt, re.M):
                declared.setdefault(f"{pkg}.{name}", p)

    print(f"       项目内共声明 {len(declared)} 个符号")

    unresolved = 0
    for p, txt in sorted(kt_files.items()):
        for imp in re.findall(r"^import\s+(com\.wordflash\.[\w.]+)", txt, re.M):
            if imp in declared:
                continue
            if any(k.startswith(imp + ".") for k in declared):
                continue
            bad(f"{os.path.basename(p)} -> {imp}")
            unresolved += 1
    if unresolved == 0:
        ok("所有 com.wordflash.* 均可解析")

    # ---------------------------------------------------------------
    # 3. 关键符号存在性
    # ---------------------------------------------------------------
    print("\n[3] 关键符号定义")
    missing = [s for s in KEY_SYMBOLS if not any(k.endswith("." + s) for k in declared)]
    if missing:
        bad(f"未找到定义: {', '.join(missing)}")
    else:
        ok(f"{len(KEY_SYMBOLS)} 个关键符号全部有定义")

    # ---------------------------------------------------------------
    # 4. 未使用的 import
    # ---------------------------------------------------------------
    print("\n[4] 未使用的 import")
    unused = 0
    for p, txt in sorted(kt_files.items()):
        body = re.sub(r"^import .*$", "", txt, flags=re.M)
        for imp in re.findall(r"^import\s+([\w.]+)", txt, re.M):
            if imp in DELEGATE_IMPORTS or imp.endswith(".*"):
                continue
            simple = imp.split(".")[-1]
            if not re.search(r"\b" + re.escape(simple) + r"\b", body):
                print(f"       未使用: {os.path.basename(p)} -> {imp}")
                unused += 1
    if unused == 0:
        ok("没有未使用的 import")
    else:
        bad(f"{unused} 个未使用的 import")

    # ---------------------------------------------------------------
    # 5. XML 合法性
    # ---------------------------------------------------------------
    print("\n[5] XML 合法性")
    xml_files = sorted(glob.glob(res + "/**/*.xml", recursive=True))
    manifest = os.path.join(root, "app", "src", "main", "AndroidManifest.xml")
    if os.path.exists(manifest):
        xml_files.append(manifest)
    xml_bad = 0
    for p in xml_files:
        try:
            ET.parse(p)
        except Exception as e:
            bad(f"{os.path.basename(p)}: {e}")
            xml_bad += 1
            continue
        txt = read(p)
        if "aapt:attr" in txt and "http://schemas.android.com/aapt" not in txt:
            bad(f"{os.path.basename(p)}: 用了 aapt:attr 但未声明命名空间")
            xml_bad += 1
    if xml_bad == 0:
        ok(f"{len(xml_files)} 个 XML 合法")

    # ---------------------------------------------------------------
    # 6. Gradle 括号配对
    # ---------------------------------------------------------------
    print("\n[6] Gradle 脚本括号配对")
    gradle_files = [
        os.path.join(root, "build.gradle.kts"),
        os.path.join(root, "settings.gradle.kts"),
        os.path.join(root, "app", "build.gradle.kts"),
    ]
    g_bad = 0
    for p in gradle_files:
        if not os.path.exists(p):
            continue
        t = read(p)
        t = re.sub(r'"""[\s\S]*?"""', '""', t)
        t = re.sub(r'"(?:[^"\\]|\\.)*"', '""', t)
        t = re.sub(r"//.*", "", t)
        t = re.sub(r"/\*[\s\S]*?\*/", "", t)
        for op, cl, label in [("{", "}", "花括号"), ("(", ")", "圆括号")]:
            if t.count(op) != t.count(cl):
                bad(f"{os.path.basename(p)}: {label}不配对 ({t.count(op)}/{t.count(cl)})")
                g_bad += 1
    if g_bad == 0:
        ok("全部配对")

    # ---------------------------------------------------------------
    # 汇总
    # ---------------------------------------------------------------
    print()
    print("=" * 52)
    if failed == 0:
        print(f"  全部通过：{passed} 项")
    else:
        print(f"  {passed} 项通过，{failed} 项失败")
    print("=" * 52)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
