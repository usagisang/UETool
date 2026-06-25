package me.ele.uetool.compose

internal class ComposeNodeInfo(
    val id: Int,
    val depth: Int
) {
    val properties: MutableMap<String, String> = LinkedHashMap()

    fun put(name: String, value: Any?) {
        val detail = value?.toString()?.takeIf { it.isNotEmpty() } ?: return
        properties[name] = detail
    }

    fun get(name: String): String? = properties[name]

    val displayName: String
        get() {
            get("TestTag")?.let { return "Compose@TestTag($it)" }
            get("Text")?.let { return "Compose@Text(${it.compact()})" }
            get("ContentDescription")?.let { return "Compose@ContentDescription(${it.compact()})" }
            get("Role")?.let { return "Compose@$it" }
            return "ComposeNode#$id"
        }

    private fun String.compact(): String {
        val compact = replace('\n', ' ')
        return if (compact.length > 32) compact.substring(0, 29) + "..." else compact
    }
}
