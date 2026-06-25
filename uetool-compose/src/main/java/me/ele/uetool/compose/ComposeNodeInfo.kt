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
        get() = nodeName.displayName

    fun pathSegment(): String = nodeName.pathSegment

    private val nodeName: NodeName by lazy(LazyThreadSafetyMode.NONE) {
        createNodeName()
    }

    private fun createNodeName(): NodeName {
        val prefix = if (isLayoutNode()) "LayoutNode" else "Compose"
        get("TestTag")?.let { return named(prefix, "TestTag") }
        if (get("IsDialog") == "true") return named(prefix, "Dialog")
        if (get("IsPopup") == "true") return named(prefix, "Popup")
        get("Role")?.let { return named(prefix, it) }
        get("Text")?.let { return named(prefix, "Text") }
        get("Actions")?.toActionName()?.let { return named(prefix, it) }
        get("PaneTitle")?.let { return named(prefix, "PaneTitle") }
        get("Error")?.let { return named(prefix, "Error") }
        if (get("Heading") == "true") return named(prefix, "Heading")
        get("ProgressBar")?.let { return named(prefix, "ProgressBar") }
        get("CollectionInfo")?.let { return named(prefix, "Collection") }
        get("LayoutType")?.let { return named(prefix, it) }
        get("ContentDescription")?.let { return named(prefix, "ContentDescription") }
        get("MeasurePolicy")?.let { return named(prefix, "Layout") }
        get("ModifierChain")?.let { return named(prefix, "Modifier") }
        get("LayoutNodeClass")?.let { return named(prefix, it) }
        return if (isLayoutNode()) {
            fallbackName("LayoutNode#$id")
        } else {
            fallbackName("ComposeNode#$id")
        }
    }

    private fun isLayoutNode(): Boolean = get("NodeSource") == "LayoutNode"

    private fun named(prefix: String, segment: String): NodeName {
        return NodeName(prefix, segment, segment.pathSafe())
    }

    private fun fallbackName(displayName: String): NodeName {
        return NodeName(null, displayName, "Node#$id".pathSafe())
    }

    private fun String.compact(): String {
        val compact = replace('\n', ' ')
        return if (compact.length > 32) compact.substring(0, 29) + "..." else compact
    }

    private fun String.pathSafe(): String {
        return compact().replace('/', '|')
    }

    private fun String.toActionName(): String? {
        return when {
            contains("OnClick") -> "Clickable"
            contains("ScrollBy") || contains("ScrollToIndex") -> "Scrollable"
            hasDisplayableAction() -> "Action"
            else -> null
        }
    }

    private fun String.hasDisplayableAction(): Boolean {
        return split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { it !in TEXT_ONLY_ACTION_NAMES }
    }

    private companion object {
        val TEXT_ONLY_ACTION_NAMES = setOf("GetTextLayoutResult")
    }

    private data class NodeName(
        private val prefix: String?,
        private val segment: String,
        val pathSegment: String
    ) {
        val displayName: String
            get() = prefix?.let { "$it@$segment" } ?: segment
    }
}
