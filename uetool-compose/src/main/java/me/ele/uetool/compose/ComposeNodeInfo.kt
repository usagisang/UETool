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
            get("PaneTitle")?.let { return "Compose@PaneTitle(${it.compact()})" }
            get("Error")?.let { return "Compose@Error(${it.compact()})" }
            get("Role")?.let { return "Compose@$it" }
            if (get("IsDialog") == "true") return "Compose@Dialog"
            if (get("IsPopup") == "true") return "Compose@Popup"
            if (get("Heading") == "true") return "Compose@Heading"
            get("Actions")?.let {
                if (it.contains("OnClick")) return "Compose@Clickable"
                if (it.contains("ScrollBy") || it.contains("ScrollToIndex")) return "Compose@Scrollable"
            }
            get("ProgressBar")?.let { return "Compose@ProgressBar" }
            get("CollectionInfo")?.let { return "Compose@Collection" }
            return "ComposeNode#$id"
        }

    fun pathSegment(): String {
        return get("TestTag")?.let { "TestTag(${it.pathSafe()})" }
            ?: get("Text")?.let { "Text(${it.pathSafe()})" }
            ?: get("ContentDescription")?.let { "ContentDescription(${it.pathSafe()})" }
            ?: get("PaneTitle")?.let { "PaneTitle(${it.pathSafe()})" }
            ?: get("Role")?.pathSafe()
            ?: get("Actions")?.let {
                when {
                    it.contains("OnClick") -> "Clickable"
                    it.contains("ScrollBy") || it.contains("ScrollToIndex") -> "Scrollable"
                    else -> "Action"
                }
            }
            ?: get("ProgressBar")?.let { "ProgressBar" }
            ?: get("CollectionInfo")?.let { "Collection" }
            ?: "Node#$id"
    }

    private fun String.compact(): String {
        val compact = replace('\n', ' ')
        return if (compact.length > 32) compact.substring(0, 29) + "..." else compact
    }

    private fun String.pathSafe(): String {
        return compact().replace('/', '|')
    }
}
