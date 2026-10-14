# -*- coding: utf-8 -*-
"""
验证 Room 数据库迁移是否真的可用。

方法：
  1. 用 v1 schema 里的 createSql 建一个真实的 SQLite 数据库
  2. 灌入几条"老用户数据"
  3. 执行 AppDatabase.kt 里写的迁移 SQL
  4. 用 SQLite 自省（PRAGMA）读出结果，与 v2 schema 的期望逐项比对

为什么必须做这一步：
  Room 只在**运行时**校验迁移结果。如果迁移产生的表结构与实体声明
  不一致，用户升级 App 后一打开就崩：
      IllegalStateException: Migration didn't properly handle: word_progress
  编译器完全不会提示这个问题。

用法：
  python tools/verify_migration.py <schema目录>
"""
import json
import os
import re
import sqlite3
import sys

# ------------------------------------------------------------------
# 从 AppDatabase.kt 里抽出的迁移 SQL（必须与源码保持一致）
# ------------------------------------------------------------------
MIGRATION_1_2 = [
    "ALTER TABLE word_progress ADD COLUMN last_wrong_at INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE word_progress ADD COLUMN is_in_wrong_book INTEGER NOT NULL DEFAULT 0",
    "CREATE INDEX IF NOT EXISTS index_word_progress_is_in_wrong_book "
    "ON word_progress (is_in_wrong_book)",
    # 把历史答错过的词补进错题本
    "UPDATE word_progress SET is_in_wrong_book = 1, last_wrong_at = last_review_at "
    "WHERE wrong_count > 0",
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


def load_schema(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)["database"]


def find_entity(db, table):
    for e in db["entities"]:
        if e["tableName"] == table:
            return e
    return None


def main():
    schema_dir = sys.argv[1]
    v1 = load_schema(os.path.join(schema_dir, "1.json"))
    v2 = load_schema(os.path.join(schema_dir, "2.json"))

    print("=" * 62)
    print("  Room 迁移验证：v1 → v2（错题本）")
    print("=" * 62)
    print()

    # ---------- 1. 用 v1 的建表语句搭出旧库 ----------
    conn = sqlite3.connect(":memory:")
    cur = conn.cursor()
    cur.execute("PRAGMA foreign_keys=ON")

    # 迁移涉及外键，需要先把被引用的表建出来。
    # 注意：建表语句（createSql）**不包含索引** —— Room 的 schema 里
    # 索引是独立列出的，必须一并执行，否则会误判"迁移丢了索引"。
    for name in ("word_book", "word", "word_progress"):
        e = find_entity(v1, name)
        if e is None:
            bad(f"v1 schema 里找不到表 {name}")
            return
        cur.execute(e["createSql"].replace("${TABLE_NAME}", name))
        for idx in e.get("indices", []):
            sql = idx.get("createSql")
            if sql:
                cur.execute(sql.replace("${TABLE_NAME}", name))
    print("  已用 v1 建表并建索引：word_book / word / word_progress")

    # ---------- 2. 灌入老数据 ----------
    cur.execute(
        "INSERT INTO word_book (id,name,description,category,word_count,"
        "is_built_in,accent_color,sort_order,created_at) "
        "VALUES (1,'四级','','CET4',3,1,0,1,0)"
    )
    cur.execute(
        "INSERT INTO word (id,book_id,spelling,phonetic,meaning,part_of_speech,"
        "example,example_translation,audio_url,difficulty) "
        "VALUES (1,1,'abandon','/x/','放弃','v.','','','',3)"
    )
    cur.execute(
        "INSERT INTO word (id,book_id,spelling,phonetic,meaning,part_of_speech,"
        "example,example_translation,audio_url,difficulty) "
        "VALUES (2,1,'absorb','/y/','吸收','v.','','','',3)"
    )
    # 词 1：曾答错 2 次；词 2：全对
    cur.execute(
        "INSERT INTO word_progress (word_id,state,repetitions,interval_days,"
        "ease_factor,next_review_at,last_review_at,correct_count,wrong_count,review_count) "
        "VALUES (1,1,0,0,2.3,100,555,1,2,3)"
    )
    cur.execute(
        "INSERT INTO word_progress (word_id,state,repetitions,interval_days,"
        "ease_factor,next_review_at,last_review_at,correct_count,wrong_count,review_count) "
        "VALUES (2,2,3,4,2.7,200,666,3,0,3)"
    )
    conn.commit()
    print("  已灌入 2 条老数据（词1 错过 2 次，词2 全对）")
    print()

    # ---------- 3. 执行迁移 ----------
    print("  执行迁移 SQL：")
    try:
        for sql in MIGRATION_1_2:
            cur.execute(sql)
            print(f"    · {sql[:70]}{'...' if len(sql) > 70 else ''}")
        conn.commit()
    except sqlite3.Error as e:
        bad(f"迁移执行失败：{e}")
        return
    ok("迁移 SQL 全部执行成功")
    print()

    # ---------- 4. 结构比对 ----------
    print("  结构比对（迁移结果 vs v2 期望）：")
    expect = find_entity(v2, "word_progress")
    actual_cols = {
        r[1]: {"type": r[2], "notnull": r[3], "dflt": r[4]}
        for r in cur.execute("PRAGMA table_info(word_progress)")
    }

    for f in expect["fields"]:
        name = f["columnName"]
        if name not in actual_cols:
            bad(f"缺少字段 {name}")
            continue
        a = actual_cols[name]
        want_type = f["affinity"]
        want_nn = 1 if f["notNull"] else 0
        if a["type"].upper() == want_type.upper() and a["notnull"] == want_nn:
            ok(f"字段 {name:20s} {a['type']:8s} NOT NULL={bool(a['notnull'])}")
        else:
            bad(f"字段 {name}: 实际 {a['type']}/notnull={a['notnull']}，"
                f"期望 {want_type}/notnull={want_nn}")

    # 索引比对
    actual_idx = {
        r[1] for r in cur.execute("PRAGMA index_list(word_progress)")
    }
    for idx in expect["indices"]:
        if idx["name"] in actual_idx:
            ok(f"索引 {idx['name']}")
        else:
            bad(f"缺少索引 {idx['name']}")

    # ---------- 5. 数据校验 ----------
    print()
    print("  老数据迁移结果：")
    row1 = cur.execute(
        "SELECT last_wrong_at, is_in_wrong_book, wrong_count FROM word_progress WHERE word_id=1"
    ).fetchone()
    row2 = cur.execute(
        "SELECT last_wrong_at, is_in_wrong_book, wrong_count FROM word_progress WHERE word_id=2"
    ).fetchone()

    print(f"    词1（曾答错 2 次）: last_wrong_at={row1[0]} is_in_wrong_book={row1[1]} wrong_count={row1[2]}")
    print(f"    词2（全对）      : last_wrong_at={row2[0]} is_in_wrong_book={row2[1]} wrong_count={row2[2]}")

    if row1[1] == 1 and row1[0] == 555:
        ok("答错过的词被补进错题本，且用 last_review_at 近似答错时间")
    else:
        bad(f"词1 迁移结果不符：期望 is_in_wrong_book=1, last_wrong_at=555")

    if row2[1] == 0 and row2[0] == 0:
        ok("全对的词未被误加进错题本")
    else:
        bad(f"词2 不应进错题本，实际 is_in_wrong_book={row2[1]}")

    conn.close()

    print()
    print("=" * 62)
    if failed == 0:
        print(f"  迁移可用：{passed} 项全部通过")
        print("  => 老用户升级不会因结构不符而崩溃")
    else:
        print(f"  {passed} 项通过，{failed} 项失败")
    print("=" * 62)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
