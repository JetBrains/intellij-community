/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.repositories

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import javax.swing.JTree

internal class RepositoryTreeItemRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
        val item = TreeUtil.getUserObject(RepositoryTreeItem::class.java, value) ?: return
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
                icon = item.module.moduleType.packageIcon
                append(item.module.getFullName())
            }
        }

        SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, true, selected)
    }
}
