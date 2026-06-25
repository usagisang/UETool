package me.ele.uetool.compose

import android.graphics.Rect
import android.view.View
import me.ele.uetool.base.Element

internal class ComposeElement(
    hostView: View,
    rect: Rect,
    val nodeInfo: ComposeNodeInfo,
    parentElement: Element?
) : Element(hostView, rect) {

    init {
        setParentElement(parentElement)
    }

    override fun reset() {
        // Compose nodes are virtual snapshots from the semantics tree.
    }

    override fun isVirtual(): Boolean = true

    override fun getElementName(): String = nodeInfo.displayName

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComposeElement) return false
        return view == other.view && nodeInfo.id == other.nodeInfo.id
    }

    override fun hashCode(): Int {
        var result = view.hashCode()
        result = 31 * result + nodeInfo.id
        return result
    }
}
