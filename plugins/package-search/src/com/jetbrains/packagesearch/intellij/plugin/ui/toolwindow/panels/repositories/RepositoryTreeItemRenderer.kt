package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

internal class RepositoryTreeItemRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
        if (value !is DefaultMutableTreeNode) return
        val item = value.userObject as? RepositoryTreeItem ?: return
        clear()

        @Suppress("MagicNumber") // Swing dimension constants
        iconTextGap = 4.scaled()

        when (item) {
            is RepositoryTreeItem.Repository -> {
                icon = AllIcons.Nodes.ModuleGroup

                append(item.repositoryModel.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)

                append(" ")
                append(
                    PackageSearchBundle.message("packagesearch.repository.canBeSearched"),
                    SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES
                )
            }
            is RepositoryTreeItem.Module -> {
                icon = item.usageInfo.projectModule.moduleType.packageIcon

                item.usageInfo.projectModule.let {
                    append(it.getFullName())
                }
            }
        }

        SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, true, selected)
    }
}
