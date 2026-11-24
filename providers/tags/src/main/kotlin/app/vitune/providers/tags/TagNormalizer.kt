package app.vitune.providers.tags

/**
 * 把任意文字（YouTube Music 分類名稱、Last.fm 標籤）轉成 [Tag]。
 * 1. 先比完整別名（忽略大小寫、連字號與空白差異）
 * 2. 再找字詞邊界上的別名，長的別名優先（避免 "j-rock" 被當成 rock、"lofi hip hop" 被當成 hip hop）
 */
object TagNormalizer {
    private fun key(raw: String) = raw
        .lowercase()
        .replace("&amp;", "&")
        .replace(Regex("[\\s_\\-/]+"), " ")
        .replace(Regex("[^\\p{L}\\p{N}& ]"), "")
        .trim()

    private val exact: Map<String, Tag> = buildMap {
        // enum 順序就是優先順序：同一個別名只歸給先出現的分類
        Tag.entries.forEach { tag -> tag.aliases.forEach { putIfAbsent(key(it), tag) } }
    }

    private val partial: List<Pair<Regex, Tag>> = exact.entries
        .sortedByDescending { it.key.length }
        .map { (alias, tag) ->
            val boundary = if (alias.any { it.code > 0x2E80 }) "" else "\\b"   // 中日韓文字沒有字詞邊界
            Regex(boundary + Regex.escape(alias) + boundary) to tag
        }

    fun normalize(raw: String?): Tag? {
        if (raw.isNullOrBlank()) return null
        val k = key(raw)
        if (k.isEmpty()) return null
        exact[k]?.let { return it }
        // 年代分類（1980s、90s）不是曲風
        if (Regex("^\\d{2,4}s$").matches(k)) return null
        return partial.firstOrNull { (re, _) -> re.containsMatchIn(k) }?.second
    }
}
