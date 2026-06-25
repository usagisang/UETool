package me.ele.uetool.compose

import me.ele.uetool.base.DimenUtil
import me.ele.uetool.base.Element
import me.ele.uetool.base.IAttrs
import me.ele.uetool.base.item.Item
import me.ele.uetool.base.item.TextItem
import me.ele.uetool.base.item.TitleItem

class UETComposeAttrs : IAttrs {

    override fun getAttrs(element: Element): List<Item> {
        val composeElement = element as? ComposeElement ?: return emptyList()
        val nodeInfo = composeElement.nodeInfo
        val rect = composeElement.rect
        val items = ArrayList<Item>()

        items.add(TitleItem("COMPOSE"))
        items.add(TextItem("Node", composeElement.elementName))
        items.add(TextItem("NodeId", nodeInfo.id.toString()))
        items.add(TextItem("Depth", nodeInfo.depth.toString()))
        nodeInfo.get("Path")?.let {
            items.add(TextItem("Path", it, true))
        }
        items.add(TextItem("Bounds(px)", rect.toShortString()))
        items.add(TextItem("Size(dp)", "${DimenUtil.px2dip(rect.width().toFloat(), true)} * ${DimenUtil.px2dip(rect.height().toFloat(), true)}"))

        nodeInfo.properties
            .filterKeys { it != "Semantics" && it != "Path" }
            .forEach { (name, detail) -> items.add(TextItem(name, detail, true)) }

        nodeInfo.get("Semantics")?.let {
            items.add(TextItem("Semantics", it, true))
        }
        return items
    }
}
