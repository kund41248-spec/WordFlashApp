package com.wordflash.app.util

import com.wordflash.app.data.seed.DictPackDto
import com.wordflash.app.data.seed.DictWordDto
import org.json.JSONArray
import org.json.JSONObject

/**
 * 自定义词库解析器。
 *
 * 支持两种格式，用户导入时自动识别：
 *
 * **1. CSV**（首行为表头，列名不区分大小写，顺序可任意）
 * ```csv
 * spelling,phonetic,pos,meaning,example,exampleTranslation
 * abandon,/əˈbændən/,v.,放弃；抛弃,He abandoned his car.,他把车丢弃了。
 * ```
 * 列名可省略，此时按「拼写, 音标, 词性, 释义, 例句, 例句翻译」的顺序解析。
 * 只有「拼写」和「释义」是必需的。
 *
 * **2. JSON**（与内置词库同构，见 [DictPackDto]）
 *
 * 分隔符自动嗅探：优先逗号，若某行制表符数量多于逗号则改用制表符，
 * 这样从 Excel 直接复制的 TSV 也能导入。
 */
object WordImporter {

    /** 解析结果。失败原因通过 [error] 返回，便于 UI 提示用户。 */
    data class Result(
        val pack: DictPackDto?,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = pack != null
    }

    private val HEADER_ALIASES = mapOf(
        "spelling" to listOf("spelling", "word", "单词", "拼写", "英文"),
        "phonetic" to listOf("phonetic", "pronunciation", "音标"),
        "pos" to listOf("pos", "partofspeech", "词性"),
        "meaning" to listOf("meaning", "translation", "释义", "中文", "意思", "解释"),
        "example" to listOf("example", "sentence", "例句"),
        "exampleTranslation" to listOf("exampletranslation", "sentencetranslation", "例句翻译", "例句释义")
    )

    /**
     * 根据内容自动选择解析方式。
     *
     * @param content 文件全文
     * @param fallbackName 用户导入时填写的词库名（JSON 中已有名称则优先用 JSON 的）
     */
    fun parse(content: String, fallbackName: String): Result {
        val trimmed = content.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (trimmed.isEmpty()) return Result(null, "文件内容为空")

        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            parseJson(trimmed, fallbackName)
        } else {
            parseCsv(trimmed, fallbackName)
        }
    }

    // ============================================================
    // JSON
    // ============================================================

    private fun parseJson(content: String, fallbackName: String): Result {
        return runCatching {
            // 支持两种 JSON 形态：完整词库对象，或纯单词数组
            val root = if (content.trimStart().startsWith("[")) {
                JSONObject().put("words", JSONArray(content))
            } else {
                JSONObject(content)
            }

            val arr = root.optJSONArray("words") ?: JSONArray()
            val words = ArrayList<DictWordDto>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val spelling = firstNonBlank(o, "spelling", "word", "单词")
                val meaning = firstNonBlank(o, "meaning", "translation", "释义")
                if (spelling.isEmpty() || meaning.isEmpty()) continue
                words += DictWordDto(
                    spelling = spelling,
                    phonetic = firstNonBlank(o, "phonetic", "音标"),
                    pos = firstNonBlank(o, "pos", "partOfSpeech", "词性"),
                    meaning = meaning,
                    example = firstNonBlank(o, "example", "sentence", "例句"),
                    exampleTranslation = firstNonBlank(
                        o, "exampleTranslation", "sentenceTranslation", "例句翻译"
                    ),
                    difficulty = o.optInt("difficulty", 3)
                )
            }

            if (words.isEmpty()) return Result(null, "JSON 中没有解析到有效单词")

            Result(
                DictPackDto(
                    name = root.optString("name").ifBlank { fallbackName },
                    description = root.optString("description", "用户导入"),
                    category = root.optString("category", "CUSTOM"),
                    version = root.optInt("version", 1),
                    words = words
                )
            )
        }.getOrElse { e ->
            Result(null, "JSON 解析失败：${e.message ?: "格式错误"}")
        }
    }

    private fun firstNonBlank(o: JSONObject, vararg keys: String): String {
        keys.forEach { k ->
            // optString 在键不存在时返回空串，但键存在值为 null 时返回 "null"
            if (o.has(k)) {
                val v = o.optString(k).trim()
                if (v.isNotEmpty() && v != "null") return v
            }
        }
        return ""
    }

    // ============================================================
    // CSV / TSV
    // ============================================================

    private fun parseCsv(content: String, fallbackName: String): Result {
        val rawLines = content.lines()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }

        if (rawLines.isEmpty()) return Result(null, "文件内容为空")

        // 嗅探分隔符：统计前若干行里逗号与制表符的数量
        val sample = rawLines.take(5)
        val commaCount = sample.sumOf { it.count { c -> c == ',' } }
        val tabCount = sample.sumOf { it.count { c -> c == '\t' } }
        val delimiter = if (tabCount > commaCount) '\t' else ','

        val firstCells = splitCsvLine(rawLines.first(), delimiter)
        val headerIndex = mapHeader(firstCells)

        // 判断首行是表头还是数据：如果所有列名都识别不出来，就按位置解析
        val hasHeader = headerIndex.values.any { it >= 0 }
        val dataLines = if (hasHeader) rawLines.drop(1) else rawLines

        if (dataLines.isEmpty()) return Result(null, "文件中没有数据行")

        val words = ArrayList<DictWordDto>(dataLines.size)
        var skipped = 0
        dataLines.forEach { line ->
            val cells = splitCsvLine(line, delimiter)
            val spelling = cell(cells, headerIndex["spelling"], 0).trim()
            val meaning = cell(cells, headerIndex["meaning"], 3).trim()
            if (spelling.isEmpty() || meaning.isEmpty()) {
                skipped++
                return@forEach
            }
            words += DictWordDto(
                spelling = spelling,
                phonetic = cell(cells, headerIndex["phonetic"], 1).trim(),
                pos = cell(cells, headerIndex["pos"], 2).trim(),
                meaning = meaning,
                example = cell(cells, headerIndex["example"], 4).trim(),
                exampleTranslation = cell(cells, headerIndex["exampleTranslation"], 5).trim(),
                difficulty = 3
            )
        }

        if (words.isEmpty()) {
            return Result(null, "没有解析到有效单词（共 $skipped 行缺少拼写或释义）")
        }

        val desc = if (skipped > 0) "用户导入（跳过 $skipped 行）" else "用户导入"
        return Result(
            DictPackDto(
                name = fallbackName,
                description = desc,
                category = "CUSTOM",
                words = words
            )
        )
    }

    /** 把表头列名映射到规范字段名 -> 列下标 */
    private fun mapHeader(cells: List<String>): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        cells.forEachIndexed { index, raw ->
            val normalized = raw.trim().lowercase().replace(" ", "").replace("_", "")
            if (normalized.isEmpty()) return@forEachIndexed
            HEADER_ALIASES.forEach { (canonical, aliases) ->
                if (map[canonical] == null && aliases.any { it == normalized }) {
                    map[canonical] = index
                }
            }
        }
        return map
    }

    private fun cell(cells: List<String>, index: Int?, fallback: Int): String {
        val i = index ?: fallback
        return cells.getOrElse(i) { "" }
    }

    /**
     * 按分隔符切分一行，支持双引号包裹的字段。
     *
     * CSV 里例句常含逗号（如 "He said, \"hello\""），
     * 必须正确处理引号，否则列会错位。"" 表示转义的双引号。
     */
    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    result += sb.toString()
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        result += sb.toString()
        return result
    }
}
