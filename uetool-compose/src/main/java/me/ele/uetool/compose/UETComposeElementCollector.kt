package me.ele.uetool.compose

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.AbstractComposeView
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
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
class UETComposeElementCollector : ElementCollector {

    override fun collect(view: View, elements: MutableList<Element>): Boolean {
        if (!view.isComposeHostCandidate()) {
            return false
        }

        val ownerInfo = view.findOwnerInfo(MAX_OWNER_SEARCH_DEPTH) ?: return false
        val before = elements.size
        val rootNode = ownerInfo.owner.readRootNode()
        val hostRootLayoutNode = ownerInfo.hostView.readRootLayoutNode()
        val semanticsRootLayoutNode = if (hostRootLayoutNode == null) rootNode?.readLayoutNode() else null
        val rootLayoutNode = hostRootLayoutNode ?: semanticsRootLayoutNode
        val semanticsIndex = if (rootLayoutNode != null) {
            rootNode?.buildSemanticsIndex() ?: SemanticsIndex.EMPTY
        } else {
            SemanticsIndex.EMPTY
        }
        val rootLayoutNodeSource = when {
            hostRootLayoutNode != null -> "hostView.root"
            semanticsRootLayoutNode != null -> "semanticsNode.layoutNode"
            else -> "none"
        }
        DebugLog.d(TAG) {
            "collect compose host=${ownerInfo.hostView.javaClass.name}, " +
                "rootNode=${rootNode != null}, " +
                "rootLayoutNode=${rootLayoutNode != null}, " +
                "rootLayoutNodeSource=$rootLayoutNodeSource, " +
                "rootLayoutNodeClass=${rootLayoutNode?.javaClass?.name}, " +
                "semanticsLayoutMatches=${semanticsIndex.layoutNodeCount}, " +
                "semanticsIdMatches=${semanticsIndex.semanticsNodeCount}"
        }
        if (rootLayoutNode != null) {
            val windowOffset = ownerInfo.hostView.readWindowOffset()
            val stats = LayoutCollectStats()
            collectLayoutNode(
                rootLayoutNode,
                windowOffset,
                null,
                elements,
                HashSet(),
                0,
                ROOT_PATH,
                semanticsIndex,
                stats
            )
            DebugLog.d(TAG) {
                "layout collect finished visited=${stats.visitedCount}, " +
                    "added=${stats.addedCount}, " +
                    "boundsNull=${stats.boundsNullCount}, " +
                    "boundsEmpty=${stats.boundsEmptyCount}, " +
                    "skippedDuplicate=${stats.skippedDuplicateCount}, " +
                    "totalElementsAdded=${elements.size - before}"
            }
            if (elements.size > before) {
                return true
            }
        }

        rootNode?.let {
            collectSemanticsNode(it, ownerInfo.hostView, null, elements, HashSet(), 0, ROOT_PATH)
            DebugLog.d(TAG) { "fallback semantics collect added=${elements.size - before}" }
        }
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

    private fun View.readRootLayoutNode(): Any? {
        return callNoArg("getRoot")
            ?: readField("root")
    }

    private fun SemanticsOwner.readRootNode(): SemanticsNode? {
        return callNoArg("getUnmergedRootSemanticsNode") as? SemanticsNode
            ?: callNoArg("getRootSemanticsNode") as? SemanticsNode
            ?: callByName("getRootSemanticsNode", false) as? SemanticsNode
            ?: readField("unmergedRootSemanticsNode") as? SemanticsNode
            ?: readField("rootSemanticsNode") as? SemanticsNode
    }

    private fun collectLayoutNode(
        layoutNode: Any,
        windowOffset: WindowOffset,
        parent: Element?,
        elements: MutableList<Element>,
        seenNodes: MutableSet<Int>,
        depth: Int,
        parentPath: String,
        semanticsIndex: SemanticsIndex,
        stats: LayoutCollectStats
    ) {
        val layoutNodeId = System.identityHashCode(layoutNode)
        if (!seenNodes.add(layoutNodeId)) {
            stats.skippedDuplicateCount++
            return
        }
        stats.visitedCount++

        val children = layoutNode.readLayoutChildren()
        val info = layoutNode.readLayoutNodeInfo(layoutNodeId, depth, children.size, semanticsIndex)
        val currentPath = if (depth == 0) ROOT_PATH else "$parentPath/${info.pathSegment()}"
        info.put("Path", currentPath)
        info.put("LayoutPath", currentPath)

        var currentParent = parent
        val bounds = layoutNode.readLayoutBounds(windowOffset)
        if (bounds == null) {
            stats.boundsNullCount++
            if (!stats.boundsNullSampleLogged) {
                stats.boundsNullSampleLogged = true
                layoutNode.logLayoutBoundsProbe(windowOffset)
            }
        } else if (bounds.width() <= 0 || bounds.height() <= 0) {
            stats.boundsEmptyCount++
        } else {
            val element = ComposeElement(windowOffset.hostView, bounds, info, parent)
            elements.add(element)
            stats.addedCount++
            currentParent = element
        }

        children.forEach { child ->
            collectLayoutNode(
                child,
                windowOffset,
                currentParent,
                elements,
                seenNodes,
                depth + 1,
                currentPath,
                semanticsIndex,
                stats
            )
        }
    }

    private fun collectSemanticsNode(
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
        val info = node.readSemanticsNodeInfo(depth, children.size)
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
            collectSemanticsNode(child, hostView, currentParent, elements, seenIds, depth + 1, currentPath)
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

    private fun SemanticsNode.readSemanticsNodeInfo(
        depth: Int,
        childCount: Int
    ): ComposeNodeInfo {
        val info = ComposeNodeInfo(readId(), depth)
        val config = callNoArg("getConfig") as? SemanticsConfiguration
            ?: callNoArg("getUnmergedConfig\$ui") as? SemanticsConfiguration
            ?: callNoArg("getUnmergedConfig\$ui_release") as? SemanticsConfiguration
            ?: readField("config") as? SemanticsConfiguration
            ?: readField("unmergedConfig") as? SemanticsConfiguration

        info.put("NodeSource", "Semantics")
        info.put("SemanticsChildCount", childCount)
        info.put("TouchBounds(root)", (callNoArg("getTouchBoundsInRoot") as? ComposeRect)?.toAndroidRect()?.toShortString())

        if (config != null) {
            info.putSemanticsConfiguration(config)
        }
        return info
    }

    private fun Any.readLayoutNodeInfo(
        nodeId: Int,
        depth: Int,
        childCount: Int,
        semanticsIndex: SemanticsIndex
    ): ComposeNodeInfo {
        val info = ComposeNodeInfo(nodeId, depth)
        val measurePolicy = callNoArg("getMeasurePolicy")
        val innerMeasurePolicy = measurePolicy?.unwrapMeasurePolicy()
        val semanticsConfig = callNoArg("getSemanticsConfiguration") as? SemanticsConfiguration
        val semanticsId = callNoArg("getSemanticsId")

        info.put("NodeSource", "LayoutNode")
        info.put("LayoutNodeHash", nodeId)
        info.put("LayoutNodeClass", toClassDetail())
        info.put("LayoutChildCount", childCount)
        info.put("LayoutWidth", callNoArg("getWidth"))
        info.put("LayoutHeight", callNoArg("getHeight"))
        info.put("SemanticsId", semanticsId)
        info.put("CompositeKeyHash", callNoArg("getCompositeKeyHash"))
        info.put("MeasurePolicy", measurePolicy?.toClassDetail())
        if (innerMeasurePolicy !== measurePolicy) {
            info.put("InnerMeasurePolicy", innerMeasurePolicy?.toClassDetail())
        }
        info.put("LayoutType", innerMeasurePolicy?.toLayoutType())
        (callNoArg("getInteropView") as? View)?.let { info.put("InteropView", it.javaClass.name) }
        semanticsConfig?.let { info.putSemanticsConfiguration(it) }
        semanticsIndex.find(nodeId, semanticsId)?.let { info.putMatchedSemantics(it) }
        return info
    }

    private fun SemanticsNode.readLayoutNode(): Any? {
        return callNoArg("getLayoutNode\$ui")
            ?: callNoArg("getLayoutNode\$ui_release")
            ?: callNoArg("getLayoutInfo")
            ?: readField("layoutNode")
    }

    private fun SemanticsNode.buildSemanticsIndex(): SemanticsIndex {
        val byLayoutNodeId = HashMap<Int, ComposeNodeInfo>()
        val bySemanticsId = HashMap<Int, ComposeNodeInfo>()
        collectSemanticsIndex(byLayoutNodeId, bySemanticsId, HashSet(), 0)
        return SemanticsIndex(byLayoutNodeId, bySemanticsId)
    }

    private fun SemanticsNode.collectSemanticsIndex(
        byLayoutNodeId: MutableMap<Int, ComposeNodeInfo>,
        bySemanticsId: MutableMap<Int, ComposeNodeInfo>,
        seenIds: MutableSet<Int>,
        depth: Int
    ) {
        val nodeId = readId()
        if (!seenIds.add(nodeId)) {
            return
        }

        val children = readChildren()
        val info = readSemanticsNodeInfo(depth, children.size)
        bySemanticsId.putOrMergeSemantics(nodeId, info)
        readLayoutNode()?.let { layoutNode ->
            byLayoutNodeId.putOrMergeSemantics(System.identityHashCode(layoutNode), info)
        }
        children.forEach { child ->
            child.collectSemanticsIndex(byLayoutNodeId, bySemanticsId, seenIds, depth + 1)
        }
    }

    private fun Any.readLayoutChildren(): List<Any> {
        return callNoArg("getChildren\$ui").toObjectList()
            ?: callNoArg("getChildren\$ui_release").toObjectList()
            ?: callNoArg("getFoldedChildren\$ui").toObjectList()
            ?: callNoArg("getFoldedChildren\$ui_release").toObjectList()
            ?: callNoArg("getChildren").toObjectList()
            ?: readField("children").toObjectList()
            ?: readField("foldedChildren").toObjectList()
            ?: emptyList()
    }

    private fun Any.readLayoutBounds(windowOffset: WindowOffset): Rect? {
        val coordinates = readLayoutCoordinates() ?: return null
        val bounds = coordinates.readBoundsInWindow(logFailure = false) ?: return null
        return bounds.toScreenRect(windowOffset)
    }

    private fun LayoutCoordinates.readBoundsInWindow(logFailure: Boolean): ComposeRect? {
        val boundsInWindowResult = runCatching { boundsInWindow() }
        if (boundsInWindowResult.isSuccess) {
            return boundsInWindowResult.getOrNull()
        }

        val fallbackResult = runCatching {
            val position = localToWindow(Offset.Zero)
            ComposeRect(
                position.x,
                position.y,
                position.x + size.width,
                position.y + size.height
            )
        }
        if (fallbackResult.isSuccess) {
            return fallbackResult.getOrNull()
        }

        if (logFailure) {
            val boundsInWindowError = boundsInWindowResult.exceptionOrNull()?.debugMessage()
            val localToWindowError = fallbackResult.exceptionOrNull()?.debugMessage()
            val isAttachedValue = runCatching { isAttached }.getOrNull()
            val sizeText = runCatching { "${size.width}x${size.height}" }.getOrNull()
            DebugLog.d(TAG) {
                "layout bounds failed coordinateClass=${javaClass.name}, " +
                    "boundsInWindowError=$boundsInWindowError, " +
                    "localToWindowError=$localToWindowError, " +
                    "isAttached=$isAttachedValue, " +
                    "size=$sizeText"
            }
        }
        return null
    }

    private fun Any.readLayoutCoordinates(): LayoutCoordinates? {
        return callNoArg("getCoordinates") as? LayoutCoordinates
            ?: callNoArg("getCoordinates\$ui") as? LayoutCoordinates
            ?: callNoArg("getCoordinates\$ui_release") as? LayoutCoordinates
            ?: callNoArg("getOuterCoordinator\$ui") as? LayoutCoordinates
            ?: callNoArg("getOuterCoordinator\$ui_release") as? LayoutCoordinates
            ?: callNoArg("getOuterCoordinator") as? LayoutCoordinates
            ?: callNoArg("getInnerCoordinator\$ui") as? LayoutCoordinates
            ?: callNoArg("getInnerCoordinator\$ui_release") as? LayoutCoordinates
            ?: callNoArg("getInnerCoordinator") as? LayoutCoordinates
            ?: readField("coordinates") as? LayoutCoordinates
            ?: readField("outerCoordinator") as? LayoutCoordinates
            ?: readField("innerCoordinator") as? LayoutCoordinates
            ?: readField("coordinator") as? LayoutCoordinates
            ?: readLayoutDelegate()?.readLayoutCoordinatesFromDelegate()
    }

    private fun Any.readLayoutDelegate(): Any? {
        return callNoArg("getLayoutDelegate\$ui") ?: callNoArg("getLayoutDelegate\$ui_release")
            ?: callNoArg("getLayoutDelegate")
            ?: readField("layoutDelegate")
    }

    private fun Any.readLayoutCoordinatesFromDelegate(): LayoutCoordinates? {
        return callNoArg("getCoordinates") as? LayoutCoordinates
            ?: callNoArg("getCoordinates\$ui") as? LayoutCoordinates
            ?: callNoArg("getCoordinates\$ui_release") as? LayoutCoordinates
            ?: callNoArg("getOuterCoordinator\$ui") as? LayoutCoordinates
            ?: callNoArg("getOuterCoordinator\$ui_release") as? LayoutCoordinates
            ?: callNoArg("getOuterCoordinator") as? LayoutCoordinates
            ?: callNoArg("getInnerCoordinator\$ui") as? LayoutCoordinates
            ?: callNoArg("getInnerCoordinator\$ui_release") as? LayoutCoordinates
            ?: callNoArg("getInnerCoordinator") as? LayoutCoordinates
            ?: readField("coordinates") as? LayoutCoordinates
            ?: readField("outerCoordinator") as? LayoutCoordinates
            ?: readField("innerCoordinator") as? LayoutCoordinates
            ?: readField("coordinator") as? LayoutCoordinates
            ?: readField("measurePassDelegate").readNestedLayoutCoordinates()
            ?: readField("lookaheadPassDelegate").readNestedLayoutCoordinates()
    }

    private fun Any?.readNestedLayoutCoordinates(): LayoutCoordinates? {
        val target = this ?: return null
        return target as? LayoutCoordinates
            ?: target.callNoArg("getCoordinates") as? LayoutCoordinates
            ?: target.callNoArg("getCoordinates\$ui") as? LayoutCoordinates
            ?: target.callNoArg("getCoordinates\$ui_release") as? LayoutCoordinates
            ?: target.readField("coordinates") as? LayoutCoordinates
            ?: target.readField("outerCoordinator") as? LayoutCoordinates
            ?: target.readField("innerCoordinator") as? LayoutCoordinates
            ?: target.readField("coordinator") as? LayoutCoordinates
    }

    private fun Any.logLayoutBoundsProbe(windowOffset: WindowOffset) {
        val coordinates = readLayoutCoordinates()
        if (coordinates == null) {
            DebugLog.d(TAG) { "layout bounds failed nodeClass=${javaClass.name}, coordinates=null" }
            return
        }

        val bounds = coordinates.readBoundsInWindow(logFailure = true)
        DebugLog.d(TAG) {
            "layout bounds probe nodeClass=${javaClass.name}, " +
                "coordinateClass=${coordinates.javaClass.name}, " +
                "bounds=${bounds?.toScreenRect(windowOffset)?.toShortString()}"
        }
    }

    private fun Throwable.debugMessage(): String {
        return "${javaClass.simpleName}:${message ?: cause?.message.orEmpty()}"
    }

    private fun SemanticsNode.readChildren(): List<SemanticsNode> {
        return (callNoArg("getChildren") as? List<*>)?.filterIsInstance<SemanticsNode>()
            ?: (callByName("getChildren", false) as? List<*>)?.filterIsInstance<SemanticsNode>()
            ?: (callByName("getChildren", false, true) as? List<*>)?.filterIsInstance<SemanticsNode>()
            ?: (callByName("getChildren", false, true, false) as? List<*>)?.filterIsInstance<SemanticsNode>()
            ?: (callNoArg("getReplacedChildren\$ui") as? List<*>)?.filterIsInstance<SemanticsNode>()
            ?: (callNoArg("getReplacedChildren\$ui_release") as? List<*>)?.filterIsInstance<SemanticsNode>()
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

    private fun ComposeRect.toScreenRect(windowOffset: WindowOffset): Rect {
        return toAndroidRect().also { rect ->
            rect.offset(windowOffset.x, windowOffset.y)
        }
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

    private fun ComposeNodeInfo.putSemanticsConfiguration(config: SemanticsConfiguration) {
        val semantics = config.readProperties()
        if (semantics.isEmpty() && !config.isMergingSemanticsOfDescendants && !config.isClearingSemantics) {
            return
        }

        semantics.putDetail(this, "Text")
        semantics.putDetail(this, "ContentDescription")
        semantics.putDetail(this, "TestTag")
        semantics.putDetail(this, "Role")
        semantics.putDetail(this, "StateDescription")
        semantics.putDetail(this, "PaneTitle")
        semantics.putDetail(this, "Error")
        semantics.putDetail(this, "ProgressBar", "ProgressBarRangeInfo")
        semantics.putDetail(this, "CollectionInfo")
        semantics.putDetail(this, "CollectionItemInfo")
        semantics.putDetail(this, "LiveRegion")
        semantics.putDetail(this, "IsTraversalGroup")
        semantics.putDetail(this, "TraversalIndex")
        semantics.putDetail(this, "HorizontalScroll", "HorizontalScrollAxisRange")
        semantics.putDetail(this, "VerticalScroll", "VerticalScrollAxisRange")
        semantics.putDetail(this, "ContentType")
        semantics.putDetail(this, "ContentDataType")
        semantics.putDetail(this, "EditableText")
        semantics.putDetail(this, "InputText")
        semantics.putDetail(this, "TextSubstitution")
        semantics.putDetail(this, "IsShowingTextSubstitution")
        semantics.putDetail(this, "TextSelectionRange")
        semantics.putDetail(this, "ImeAction")
        semantics.putDetail(this, "Selected")
        semantics.putDetail(this, "ToggleableState")
        semantics.putDetail(this, "Focused")
        semantics.putDetail(this, "IsEditable")
        semantics.putDetail(this, "MaxTextLength")
        semantics.putDetail(this, "TestTagsAsResourceId")
        semantics.putFlag(this, "Heading")
        semantics.putFlag(this, "SelectableGroup")
        semantics.putFlag(this, "Disabled")
        semantics.putFlag(this, "Password")
        semantics.putFlag(this, "HideFromAccessibility")
        semantics.putFlag(this, "IsPopup")
        semantics.putFlag(this, "IsDialog")
        put("MergeDescendants", config.isMergingSemanticsOfDescendants)
        put("ClearingSemantics", config.isClearingSemantics)
        put("SemanticsPropertyCount", semantics.size)
        putActions(semantics.readActions())
        putOtherSemantics(semantics)
    }

    private fun ComposeNodeInfo.putMatchedSemantics(matchedSemantics: ComposeNodeInfo) {
        put("MatchedSemanticsId", matchedSemantics.id)
        put("MatchedSemanticsDepth", matchedSemantics.depth)
        matchedSemantics.properties
            .filterKeys { it !in SEMANTICS_MATCH_IGNORED_PROPERTY_NAMES }
            .forEach { (name, detail) -> properties[name] = detail }
    }

    private fun MutableMap<Int, ComposeNodeInfo>.putOrMergeSemantics(
        key: Int,
        info: ComposeNodeInfo
    ) {
        val existing = this[key]
        if (existing == null) {
            this[key] = info
            return
        }
        info.properties
            .filterKeys { it !in SEMANTICS_MATCH_IGNORED_PROPERTY_NAMES }
            .forEach { (name, detail) -> existing.properties[name] = detail }
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

    private fun Any.toClassDetail(): String {
        val simpleName = javaClass.simpleName.takeIf { it.isNotBlank() }
        return simpleName ?: javaClass.name
    }

    private fun Any.toLayoutType(): String? {
        val className = javaClass.name
        val simpleName = javaClass.simpleName.takeIf { it.isNotBlank() }
        val generatedLayoutType = className.toGeneratedLayoutType()
        val knownLayoutType = simpleName?.let { KNOWN_MEASURE_POLICY_LAYOUT_TYPES[it] }
        return when {
            knownLayoutType != null -> knownLayoutType
            className.contains("Column", ignoreCase = true) -> "Column"
            className.contains("Row", ignoreCase = true) -> "Row"
            className.contains("Box", ignoreCase = true) -> "Box"
            className.contains("Lazy", ignoreCase = true) -> "LazyLayout"
            className.contains("Subcompose", ignoreCase = true) -> "SubcomposeLayout"
            generatedLayoutType != null -> generatedLayoutType
            simpleName != null && '$' !in simpleName && simpleName.endsWith("MeasurePolicy") -> {
                simpleName.removeSuffix("MeasurePolicy").takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }

    private fun Any.unwrapMeasurePolicy(): Any {
        var current = this
        val seen = HashSet<Int>()
        repeat(MAX_MEASURE_POLICY_UNWRAP_DEPTH) {
            if (!seen.add(System.identityHashCode(current))) {
                return current
            }
            current.readWrappedMeasurePolicy()?.let { wrapped ->
                if (wrapped !== current) {
                    current = wrapped
                    return@repeat
                }
            }
            return current
        }
        return current
    }

    private fun Any.readWrappedMeasurePolicy(): Any? {
        val simpleName = javaClass.simpleName
        return when (simpleName) {
            "MultiContentMeasurePolicyImpl" -> readField("measurePolicy")
            else -> null
        }
    }

    private fun String.toGeneratedLayoutType(): String? {
        val generatedClassName = substringAfterLast('.').substringBefore('$')
        return GENERATED_LAYOUT_TYPES[generatedClassName]
    }

    private fun Any?.toObjectList(): List<Any>? {
        return when (this) {
            is List<*> -> filterNotNull()
            is Iterable<*> -> filterNotNull()
            else -> null
        }
    }

    private fun Any.callNoArg(name: String): Any? = callByName(name)

    private fun Any.callByName(name: String, vararg args: Any?): Any? {
        val method = javaClass.findMethod(name, args.size) ?: return null
        return runCatching {
            method.invoke(this, *args)
        }.getOrNull()
    }

    private fun Class<*>.findMethod(name: String, parameterCount: Int): Method? {
        val key = "${name}#${parameterCount}"
        synchronized(methodCache) {
            methodCache[this]?.let { cache ->
                if (cache.containsKey(key)) {
                    return cache[key] as? Method
                }
            }
        }

        val method = methods.firstOrNull { it.name == name && it.parameterTypes.size == parameterCount }
            ?: findDeclaredMethod(name, parameterCount)
        runCatching { method?.isAccessible = true }
        synchronized(methodCache) {
            methodCache.getOrPut(this) { HashMap() }[key] = method ?: NO_MEMBER
        }
        return method
    }

    private fun Class<*>.findDeclaredMethod(name: String, parameterCount: Int): Method? {
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
        val field = javaClass.findField(name) ?: return null
        return runCatching { field.get(this) }.getOrNull()
    }

    private fun Class<*>.findField(name: String): Field? {
        synchronized(fieldCache) {
            fieldCache[this]?.let { cache ->
                if (cache.containsKey(name)) {
                    return cache[name] as? Field
                }
            }
        }

        var current: Class<*>? = this
        while (current != null) {
            val currentClass = current
            val field = runCatching {
                currentClass.getDeclaredField(name).also { field ->
                    field.isAccessible = true
                }
            }.getOrNull()
            if (field != null) {
                synchronized(fieldCache) {
                    fieldCache.getOrPut(this) { HashMap() }[name] = field
                }
                return field
            }
            current = current.superclass
        }

        synchronized(fieldCache) {
            fieldCache.getOrPut(this) { HashMap() }[name] = NO_MEMBER
        }
        return null
    }

    private fun View.readWindowOffset(): WindowOffset {
        val screen = IntArray(2)
        val window = IntArray(2)
        getLocationOnScreen(screen)
        getLocationInWindow(window)
        return WindowOffset(this, screen[0] - window[0], screen[1] - window[1])
    }

    private data class WindowOffset(
        val hostView: View,
        val x: Int,
        val y: Int
    )

    private data class LayoutCollectStats(
        var visitedCount: Int = 0,
        var addedCount: Int = 0,
        var boundsNullCount: Int = 0,
        var boundsEmptyCount: Int = 0,
        var boundsNullSampleLogged: Boolean = false,
        var skippedDuplicateCount: Int = 0
    )

    private data class OwnerInfo(
        val owner: SemanticsOwner,
        val hostView: View
    )

    private data class SemanticsIndex(
        private val byLayoutNodeId: Map<Int, ComposeNodeInfo>,
        private val bySemanticsId: Map<Int, ComposeNodeInfo>
    ) {
        val layoutNodeCount: Int
            get() = byLayoutNodeId.size

        val semanticsNodeCount: Int
            get() = bySemanticsId.size

        fun find(layoutNodeId: Int, semanticsId: Any?): ComposeNodeInfo? {
            byLayoutNodeId[layoutNodeId]?.let { return it }
            val semanticsNodeId = (semanticsId as? Number)?.toInt() ?: return null
            return bySemanticsId[semanticsNodeId]
        }

        companion object {
            val EMPTY = SemanticsIndex(emptyMap(), emptyMap())
        }
    }

    private companion object {
        private val NO_MEMBER = Any()
        private val methodCache = HashMap<Class<*>, MutableMap<String, Any>>()
        private val fieldCache = HashMap<Class<*>, MutableMap<String, Any>>()
        private val GENERATED_LAYOUT_TYPES = mapOf(
            "ImageKt" to "Image"
        )
        private val KNOWN_MEASURE_POLICY_LAYOUT_TYPES = mapOf(
            "FlowMeasurePolicy" to "FlowLayout"
        )
        private const val MAX_MEASURE_POLICY_UNWRAP_DEPTH = 4
        private val SEMANTICS_MATCH_IGNORED_PROPERTY_NAMES = setOf(
            "NodeSource",
            "Path"
        )
        private const val TAG = "UETComposeCollector"
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
