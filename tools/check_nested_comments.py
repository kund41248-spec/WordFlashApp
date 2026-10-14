# -*- coding: utf-8 -*-
"""
检测 Kotlin 源码里的「嵌套注释」隐患。

背景：Kotlin 的块注释可以嵌套（与 Java 不同）。
因此注释或字符串里出现 `/*` 序列，会意外开启一层新注释，
导致后面的 `*/` 只关掉内层，最外层注释直到文件结束都没闭合，
编译报 "Unclosed comment"。

典型踩坑写法（在 KDoc 里）：
    * 把 `assets/dict/*.json` 写入数据库

这里扫描所有 Kotlin 文件，统计每个文件里 `/*` 与 `*/` 的出现次数。
正常情况下两者应当相等；若不相等，极可能有嵌套注释问题。

用法：python tools/check_nested_comments.py <项目根目录>
"""
import os
import re
import sys

# 跳过字符串字面量，避免把 "a/*b*/c" 这种误判
def strip_strings(text):
    # 三引号字符串
    text = re.sub(r'"""[\s\S]*?"""', '""', text)
    # 普通字符串（含转义）
    text = re.sub(r'"(?:[^"\\\n]|\\.)*"', '""', text)
    # 字符字面量
    text = re.sub(r"'(?:[^'\\\n]|\\.)'", "''", text)
    return text


def count_multiline_comments(text):
    """统计不在字符串里的 /* 与 */ 数量"""
    clean = strip_strings(text)
    return clean.count("/*"), clean.count("*/")


def main():
    root = sys.argv[1]
    src = os.path.join(root, "app", "src", "main", "java")

    files = []
    for dp, _, fns in os.walk(src):
        for fn in fns:
            if fn.endswith(".kt"):
                files.append(os.path.join(dp, fn))

    print(f"扫描 {len(files)} 个 Kotlin 文件\n")

    bad = 0
    for p in sorted(files):
        with open(p, encoding="utf-8") as f:
            text = f.read()

        opens, closes = count_multiline_comments(text)

        # 再精确检查：逐字符扫描，跟踪注释嵌套深度
        depth, i, n = 0, 0, len(text)
        in_line = False
        in_str = False
        in_raw = False
        max_depth = 0
        while i < n:
            c = text[i]
            nxt = text[i + 1] if i + 1 < n else ""

            if in_line:
                if c == "\n":
                    in_line = False
                i += 1
                continue

            if in_raw:
                if text.startswith('"""', i):
                    in_raw = False
                    i += 3
                    continue
                i += 1
                continue

            if in_str:
                if c == "\\":
                    i += 2
                    continue
                if c == '"':
                    in_str = False
                i += 1
                continue

            if text.startswith("//", i):
                in_line = True
                i += 2
                continue
            if text.startswith('"""', i):
                in_raw = True
                i += 3
                continue
            if c == '"':
                in_str = True
                i += 1
                continue
            if text.startswith("/*", i):
                depth += 1
                max_depth = max(max_depth, depth)
                i += 2
                continue
            if text.startswith("*/", i):
                depth -= 1
                i += 2
                continue
            i += 1

        rel = os.path.relpath(p, root)
        if depth != 0:
            print(f"  FAIL  {rel}")
            print(f"        文件结束时注释深度 = {depth}（应为 0）")
            print(f"        '/*' 出现 {opens} 次，'*/' 出现 {closes} 次")
            bad += 1
        elif max_depth > 1:
            print(f"  WARN  {rel}")
            print(f"        检测到嵌套注释（最大深度 {max_depth}），确认是有意为之")
        elif opens != closes:
            print(f"  WARN  {rel}  '/*'={opens} '*/'={closes}（可能含字符串内的注释符号）")

    print()
    if bad == 0:
        print("=" * 50)
        print("  所有文件的块注释均已正确闭合")
        print("=" * 50)
        sys.exit(0)
    else:
        print("=" * 50)
        print(f"  {bad} 个文件存在未闭合注释")
        print("=" * 50)
        sys.exit(1)


if __name__ == "__main__":
    main()
