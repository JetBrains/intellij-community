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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.modules

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class ModulesTreeItemRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ) {
        if (value !is DefaultMutableTreeNode) return
        clear()

        @Suppress("MagicNumber") // Swing dimension constants
        iconTextGap = 4.scaled()

        when (val nodeTarget = value.userObject as TargetModules) {
            TargetModules.None, is TargetModules.All -> {
                icon = AllIcons.Nodes.ModuleGroup
                append(
                    PackageSearchBundle.message("packagesearch.ui.toolwindow.allModules"),
                    SimpleTextAttributes.REGULAR_ATTRIBUTES
                )
            }
            is TargetModules.One -> {
                icon = nodeTarget.module.moduleType.icon ?: AllIcons.Nodes.Module
                append(nodeTarget.module.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }

        SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, true, selected)
    }
}
