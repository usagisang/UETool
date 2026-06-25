package me.ele.uetool.compose

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
        collectNode(rootNode, ownerInfo.hostView, null, elements, HashSet(), 0)
        return elements.size > before
    }

    private fun View.isComposeHostCandidate(): Boolean {
        if (this is AbstractComposeView || this is ComposeView) {
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
        depth: Int
    ) {
        val nodeId = node.readId()
        if (!seenIds.add(nodeId)) {
            return
        }

        var currentParent = parent
        val bounds = node.readBounds(hostView)
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            val element = ComposeElement(hostView, bounds, node.readNodeInfo(depth), parent)
            elements.add(element)
            currentParent = element
        }

        node.readChildren().forEach { child ->
            collectNode(child, hostView, currentParent, elements, seenIds, depth + 1)
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

    private fun SemanticsNode.readNodeInfo(depth: Int): ComposeNodeInfo {
        val info = ComposeNodeInfo(readId(), depth)
        val config = callNoArg("getConfig") as? SemanticsConfiguration
            ?: readField("config") as? SemanticsConfiguration
            ?: readField("unmergedConfig") as? SemanticsConfiguration

        if (config != null) {
            info.put("Text", runCatching { config.getOrNull(SemanticsProperties.Text) }.getOrNull())
            info.put("ContentDescription", runCatching { config.getOrNull(SemanticsProperties.ContentDescription) }.getOrNull())
            info.put("TestTag", runCatching { config.getOrNull(SemanticsProperties.TestTag) }.getOrNull())
            info.put("Role", runCatching { config.getOrNull(SemanticsProperties.Role) }.getOrNull())
            info.put("StateDescription", runCatching { config.getOrNull(SemanticsProperties.StateDescription) }.getOrNull())
            info.put("EditableText", runCatching { config.getOrNull(SemanticsProperties.EditableText) }.getOrNull())
            info.put("Selected", runCatching { config.getOrNull(SemanticsProperties.Selected) }.getOrNull())
            info.put("Focused", runCatching { config.getOrNull(SemanticsProperties.Focused) }.getOrNull())
            if (runCatching { config.getOrNull(SemanticsProperties.Disabled) }.getOrNull() != null) {
                info.put("Disabled", "true")
            }
            if (runCatching { config.getOrNull(SemanticsActions.OnClick) }.getOrNull() != null) {
                info.put("OnClick", "true")
            }
            if (runCatching { config.getOrNull(SemanticsActions.SetText) }.getOrNull() != null) {
                info.put("SetText", "true")
            }
            if (runCatching { config.getOrNull(SemanticsActions.ScrollBy) }.getOrNull() != null) {
                info.put("ScrollBy", "true")
            }
            info.put("Semantics", config)
        }
        info.put("MergeDescendants", callNoArg("isMergingSemanticsOfDescendants"))
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
    }
}
