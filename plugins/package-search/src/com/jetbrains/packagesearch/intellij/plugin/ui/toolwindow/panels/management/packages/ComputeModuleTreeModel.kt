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

package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.util.asSafely
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

internal fun computeModuleTreeModel(
    modules: List<PackageSearchModule>
): TreeModel {
    if (modules.isEmpty()) {
        val rootNode = DefaultMutableTreeNode(TargetModules.None)
        return DefaultTreeModel(rootNode)
    }

    val rootTargetModules = TargetModules.all(modules)
    val rootNode = DefaultMutableTreeNode(rootTargetModules)
        .appendChildren(modules.sortedBy { it.name })

    return DefaultTreeModel(rootNode)
}

private fun DefaultMutableTreeNode.appendChildren(sortedModules: List<PackageSearchModule>): DefaultMutableTreeNode {
    val childModules = when (val nodeTargetModules = userObject as TargetModules) {
        is TargetModules.None -> emptyList()
        is TargetModules.One -> sortedModules.filter { nodeTargetModules.module == it.parent }
        is TargetModules.All -> sortedModules.filter { module -> module.parent == null }
    }

    for (childModule in childModules) {
        val nodeTargetModules = TargetModules.One(childModule)
        val childNode = DefaultMutableTreeNode(nodeTargetModules)
        add(childNode)

        childNode.appendChildren(sortedModules)
    }

    return this
}

internal fun DefaultMutableTreeNode.findPathWithData(previousPath: List<String>): TreePath {
    if (previousPath.isEmpty()) return TreePath(path)
    var queue = mutableListOf(this)
    var currentIndex = 0
    while (true) {
        val currentNode = queue.removeAt(0)
        val currentSelectedModule = currentNode.userObject.asSafely<TargetModules>() ?: return TreePath(currentNode.path)

        val moduleName = when (currentSelectedModule) {
            is TargetModules.All -> "root"
            is TargetModules.One -> currentSelectedModule.module.name
            TargetModules.None -> ""
        }
        if (moduleName == previousPath[currentIndex]) {
            if (currentIndex == previousPath.lastIndex) return TreePath(currentNode.path)
            currentIndex++
            queue = currentNode.children().asSequence()
                .filterIsInstance<DefaultMutableTreeNode>()
                .toMutableList()
        }

        if (queue.isEmpty()) return TreePath(currentNode.path)
    }
}
