package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.localizedName
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class RepositoryTreeItemRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {

        if (value !is DefaultMutableTreeNode) return
        val item = value.userObject as? RepositoryItem ?: return
        clear()

        @Suppress("MagicNumber") // Gotta love Swing APIs
        iconTextGap = JBUI.scale(4)

        when (item) {
            is RepositoryItem.Group -> {
                icon = AllIcons.Nodes.ModuleGroup

                when {
                    item.meta.remoteInfo != null -> {
                        append(item.meta.remoteInfo.localizedName(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    }
                    item.meta.name != null -> {
                        append(item.meta.name.capitalize(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    }
                    item.meta.id != null -> {
                        append(item.meta.id, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    }
                }

                if (item.meta.url != null && item.meta.remoteInfo == null) {
                    append(" " + item.meta.url, SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                }

                item.meta.remoteInfo?.let {
                    append(" ")
                    append(
                        PackageSearchBundle.message("packagesearch.repository.canBeSearched"),
                        SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES
                    )
                }
            }
            is RepositoryItem.Module -> {
                icon = item.meta.projectModule?.moduleType?.packageIcon

                item.meta.projectModule?.let {
                    append(it.getFullName())
                }
            }
        }

        SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, true, selected)
    }
}
