package me.ele.uetool.compose

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.text.AnnotatedString
import me.ele.uetool.base.Element
import me.ele.uetool.base.ElementCollector
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
class UETComposeElementCollector : ElementCollector {

    override fun collect(view: View, elements: MutableList<Element>): Boolean {
        if (!view.isComposeHostCandidate()) {
            return false
        }

        val ownerInfo = view.findOwnerInfo(MAX_OWNER_SEARCH_DEPTH) ?: return false
        val rootNode = ownerInfo.owner.readRootNode() ?: return false
        val before = elements.size
        collectNode(rootNode, ownerInfo.hostView, null, elements, HashSet(), 0, ROOT_PATH)
        return elements.size > before
    }

    private fun View.isComposeHostCandidate(): Boolean {
        if (this is AbstractComposeView) {
            return true
        }
        return javaClass.name.startsWith(COMPOSE_PLATFORM_PACKAGE) && readSemanticsOwner() != null
    }

    private fun View.findOwnerInfo(depth: Int): OwnerInfo? {
        readSemanticsOwner()?.let { return OwnerInfo(it, this) }
        if (depth <= 0 || this !is ViewGroup) {
            return null
        }
        for (index in 0 until childCount) {
            getChildAt(index).findOwnerInfo(depth - 1)?.let { return it }
        }
        return null
    }

    private fun View.readSemanticsOwner(): SemanticsOwner? {
        return callNoArg("getSemanticsOwner") as? SemanticsOwner
            ?: readField("semanticsOwner") as? SemanticsOwner
    }

    private fun SemanticsOwner.readRootNode(): SemanticsNode? {
        return callNoArg("getUnmergedRootSemanticsNode") as? SemanticsNode
            ?: callNoArg("getRootSemanticsNode") as? SemanticsNode
            ?: callByName("getRootSemanticsNode", false) as? SemanticsNode
            ?: readField("unmergedRootSemanticsNode") as? SemanticsNode
            ?: readField("rootSemanticsNode") as? SemanticsNode
    }

    private fun collectNode(
        node: SemanticsNode,
        hostView: View,
        parent: Element?,
        elements: MutableList<Element>,
        seenIds: MutableSet<Int>,
        depth: Int,
        parentPath: String
    ) {
        val nodeId = node.readId()
        if (!seenIds.add(nodeId)) {
            return
        }

        val children = node.readChildren()
        val info = node.readNodeInfo(depth, children.size)
        val currentPath = if (depth == 0) ROOT_PATH else "$parentPath/${info.pathSegment()}"
        info.put("Path", currentPath)

        var currentParent = parent
        val bounds = node.readBounds(hostView)
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            val element = ComposeElement(hostView, bounds, info, parent)
            elements.add(element)
            currentParent = element
        }

        children.forEach { child ->
            collectNode(child, hostView, currentParent, elements, seenIds, depth + 1, currentPath)
        }
    }

    private fun SemanticsNode.readBounds(hostView: View): Rect? {
        val windowBounds = callNoArg("getBoundsInWindow") as? ComposeRect
            ?: readField("boundsInWindow") as? ComposeRect
        windowBounds?.toAndroidRect()?.let { rect ->
            val screen = IntArray(2)
            val window = IntArray(2)
            hostView.getLocationOnScreen(screen)
            hostView.getLocationInWindow(window)
            rect.offset(screen[0] - window[0], screen[1] - window[1])
            return rect
        }

        val rootBounds = callNoArg("getBoundsInRoot") as? ComposeRect
            ?: readField("boundsInRoot") as? ComposeRect
        return rootBounds?.toAndroidRect()?.also { rect ->
            val location = IntArray(2)
            hostView.getLocationOnScreen(location)
            rect.offset(location[0], location[1])
        }
    }

    private fun SemanticsNode.readNodeInfo(
        depth: Int,
        childCount: Int
    ): ComposeNodeInfo {
        val info = ComposeNodeInfo(readId(), depth)
        val config = callNoArg("getConfig") as? SemanticsConfiguration
            ?: readField("config") as? SemanticsConfiguration
            ?: readField("unmergedConfig") as? SemanticsConfiguration

        info.put("ChildCount", childCount)
        info.put("TouchBounds(root)", (callNoArg("getTouchBoundsInRoot") as? ComposeRect)?.toAndroidRect()?.toShortString())

        if (config != null) {
            val semantics = config.readProperties()

            semantics.putDetail(info, "Text")
            semantics.putDetail(info, "ContentDescription")
            semantics.putDetail(info, "TestTag")
            semantics.putDetail(info, "Role")
            semantics.putDetail(info, "StateDescription")
            semantics.putDetail(info, "PaneTitle")
            semantics.putDetail(info, "Error")
            semantics.putDetail(info, "ProgressBar", "ProgressBarRangeInfo")
            semantics.putDetail(info, "CollectionInfo")
            semantics.putDetail(info, "CollectionItemInfo")
            semantics.putDetail(info, "LiveRegion")
            semantics.putDetail(info, "IsTraversalGroup")
            semantics.putDetail(info, "TraversalIndex")
            semantics.putDetail(info, "HorizontalScroll", "HorizontalScrollAxisRange")
            semantics.putDetail(info, "VerticalScroll", "VerticalScrollAxisRange")
            semantics.putDetail(info, "ContentType")
            semantics.putDetail(info, "ContentDataType")
            semantics.putDetail(info, "EditableText")
            semantics.putDetail(info, "InputText")
            semantics.putDetail(info, "TextSubstitution")
            semantics.putDetail(info, "IsShowingTextSubstitution")
            semantics.putDetail(info, "TextSelectionRange")
            semantics.putDetail(info, "ImeAction")
            semantics.putDetail(info, "Selected")
            semantics.putDetail(info, "ToggleableState")
            semantics.putDetail(info, "Focused")
            semantics.putDetail(info, "IsEditable")
            semantics.putDetail(info, "MaxTextLength")
            semantics.putDetail(info, "TestTagsAsResourceId")
            semantics.putFlag(info, "Heading")
            semantics.putFlag(info, "SelectableGroup")
            semantics.putFlag(info, "Disabled")
            semantics.putFlag(info, "Password")
            semantics.putFlag(info, "HideFromAccessibility")
            semantics.putFlag(info, "IsPopup")
            semantics.putFlag(info, "IsDialog")
            info.put("MergeDescendants", config.isMergingSemanticsOfDescendants)
            info.put("ClearingSemantics", config.isClearingSemantics)
            info.putActions(semantics.readActions())
            info.putOtherSemantics(semantics)
            info.put("Semantics", config)
        }
        return info
    }

    private fun SemanticsNode.readChildren(): List<SemanticsNode> {
        return (callNoArg("getChildren") as? List<*>)?.filterIsInstance<SemanticsNode>()
            ?: (callByName("getChildren", false) as? List<*>)?.filterIsInstance<SemanticsNode>()
            ?: (callByName("getChildren", false, true) as? List<*>)?.filterIsInstance<SemanticsNode>()
            ?: (callByName("getChildren", false, true, false) as? List<*>)?.filterIsInstance<SemanticsNode>()
            ?: (callNoArg("getReplacedChildren") as? List<*>)?.filterIsInstance<SemanticsNode>()
            ?: (callByName("getReplacedChildren", false) as? List<*>)?.filterIsInstance<SemanticsNode>()
            ?: (readField("children") as? List<*>)?.filterIsInstance<SemanticsNode>()
            ?: emptyList()
    }

    private fun SemanticsNode.readId(): Int {
        return (callNoArg("getId") as? Number)?.toInt()
            ?: (readField("id") as? Number)?.toInt()
            ?: hashCode()
    }

    private fun ComposeRect.toAndroidRect(): Rect {
        return Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
    }

    private fun AnnotatedString.toPlainText(): String {
        return text
    }

    private fun ProgressBarRangeInfo.toDetail(): String {
        return "current=$current, range=${range.start}..${range.endInclusive}, steps=$steps"
    }

    private fun CollectionInfo.toDetail(): String {
        return "rows=$rowCount, columns=$columnCount"
    }

    private fun CollectionItemInfo.toDetail(): String {
        return "row=$rowIndex, rowSpan=$rowSpan, column=$columnIndex, columnSpan=$columnSpan"
    }

    private fun ScrollAxisRange.toDetail(): String {
        val current = runCatching { value() }.getOrNull()?.toString() ?: "?"
        val max = runCatching { maxValue() }.getOrNull()?.toString() ?: "?"
        return "value=$current, max=$max, reverse=$reverseScrolling"
    }

    private fun AccessibilityAction<*>.toAccessibilityActionDetail(): String {
        return label?.takeIf { it.isNotBlank() } ?: "true"
    }

    private fun SemanticsConfiguration.readProperties(): Map<String, Any?> {
        val properties = LinkedHashMap<String, Any?>()
        for (entry in this) {
            properties[entry.key.name] = entry.value
        }
        return properties
    }

    private fun Map<String, Any?>.putDetail(
        info: ComposeNodeInfo,
        name: String,
        sourceName: String = name
    ) {
        val value = this[sourceName] ?: return
        info.put(name, value.toSemanticsDetail())
    }

    private fun Map<String, Any?>.putFlag(
        info: ComposeNodeInfo,
        name: String,
        sourceName: String = name
    ) {
        if (containsKey(sourceName)) {
            info.put(name, "true")
        }
    }

    private fun Map<String, Any?>.readActions(): Map<String, String> {
        val actions = LinkedHashMap<String, String>()
        ACTION_NAMES.forEach { name ->
            this[name]?.let { actions[name] = it.toActionDetail() }
        }
        return actions
    }

    private fun Any.toActionDetail(): String {
        return when (this) {
            is AccessibilityAction<*> -> this.toAccessibilityActionDetail()
            is List<*> -> toCustomActionsDetail()
            else -> "true"
        }
    }

    private fun List<*>.toCustomActionsDetail(): String {
        val labels = mapNotNull {
            (it?.callNoArg("getLabel") as? String)?.takeIf { label -> label.isNotBlank() }
        }
        return labels.takeIf { it.isNotEmpty() }?.joinToString() ?: size.toString()
    }

    private fun Any.toSemanticsDetail(): String {
        return when (this) {
            is AnnotatedString -> toPlainText()
            is List<*> -> toListDetail()
            is ProgressBarRangeInfo -> toDetail()
            is CollectionInfo -> toDetail()
            is CollectionItemInfo -> toDetail()
            is ScrollAxisRange -> toDetail()
            is Unit -> "true"
            else -> toString()
        }
    }

    private fun List<*>.toListDetail(): String {
        return mapNotNull {
            when (it) {
                is AnnotatedString -> it.text
                null -> null
                else -> it.toString()
            }
        }.joinToString()
    }

    private fun ComposeNodeInfo.putActions(actions: Map<String, String>) {
        if (actions.isEmpty()) {
            return
        }
        put("Actions", actions.keys.joinToString())
        actions.forEach { (name, detail) -> put(name, detail) }
    }

    private fun ComposeNodeInfo.putOtherSemantics(semantics: Map<String, Any?>) {
        semantics
            .filterKeys { it !in KNOWN_PROPERTY_NAMES && it !in ACTION_NAMES }
            .forEach { (name, detail) -> detail?.let { put(name, it.toSemanticsDetail()) } }
    }

    private fun Any.callNoArg(name: String): Any? = callByName(name)

    private fun Any.callByName(name: String, vararg args: Any?): Any? {
        val method = javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.size == args.size }
            ?: javaClass.findDeclaredMethod(name, args.size)
            ?: return null
        return runCatching {
            method.isAccessible = true
            method.invoke(this, *args)
        }.getOrNull()
    }

    private fun Class<*>.findDeclaredMethod(name: String, parameterCount: Int): java.lang.reflect.Method? {
        var current: Class<*>? = this
        while (current != null) {
            current.declaredMethods.firstOrNull {
                it.name == name && it.parameterTypes.size == parameterCount
            }?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun Any.readField(name: String): Any? {
        var current: Class<*>? = javaClass
        while (current != null) {
            val value = runCatching {
                val field = current.getDeclaredField(name)
                field.isAccessible = true
                field.get(this)
            }.getOrNull()
            if (value != null) {
                return value
            }
            current = current.superclass
        }
        return null
    }

    private data class OwnerInfo(
        val owner: SemanticsOwner,
        val hostView: View
    )

    private companion object {
        const val COMPOSE_PLATFORM_PACKAGE = "androidx.compose.ui.platform."
        const val MAX_OWNER_SEARCH_DEPTH = 4
        const val ROOT_PATH = "Root"
        val ACTION_NAMES = setOf(
            "OnClick",
            "OnLongClick",
            "ScrollBy",
            "ScrollByOffset",
            "ScrollToIndex",
            "SetProgress",
            "SetSelection",
            "SetText",
            "OnAutofillText",
            "SetTextSubstitution",
            "ShowTextSubstitution",
            "ClearTextSubstitution",
            "InsertTextAtCursor",
            "OnImeAction",
            "CopyText",
            "CutText",
            "PasteText",
            "Expand",
            "Collapse",
            "Dismiss",
            "RequestFocus",
            "PageUp",
            "PageLeft",
            "PageDown",
            "PageRight",
            "GetTextLayoutResult",
            "GetScrollViewportLength",
            "CustomActions"
        )
        val KNOWN_PROPERTY_NAMES = setOf(
            "Text",
            "ContentDescription",
            "TestTag",
            "Role",
            "StateDescription",
            "PaneTitle",
            "Error",
            "ProgressBarRangeInfo",
            "CollectionInfo",
            "CollectionItemInfo",
            "LiveRegion",
            "IsTraversalGroup",
            "TraversalIndex",
            "HorizontalScrollAxisRange",
            "VerticalScrollAxisRange",
            "ContentType",
            "ContentDataType",
            "EditableText",
            "InputText",
            "TextSubstitution",
            "IsShowingTextSubstitution",
            "TextSelectionRange",
            "ImeAction",
            "Selected",
            "ToggleableState",
            "Focused",
            "IsEditable",
            "MaxTextLength",
            "TestTagsAsResourceId",
            "Heading",
            "SelectableGroup",
            "Disabled",
            "Password",
            "HideFromAccessibility",
            "IsPopup",
            "IsDialog"
        )
    }
}
