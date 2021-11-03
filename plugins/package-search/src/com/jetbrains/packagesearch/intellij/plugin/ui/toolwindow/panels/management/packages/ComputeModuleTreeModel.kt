package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

internal data class ModulesTreeData(
    val treeModel: TreeModel,
    val selectedPath: TreePath
)

internal fun computeModuleTreeModel(
    modules: List<ModuleModel>
): TreeModel {
    if (modules.isEmpty()) {
        val rootNode = DefaultMutableTreeNode(TargetModules.None)
        return DefaultTreeModel(rootNode)
    }

    val sortedModules = modules.sortedBy { it.projectModule.name }
        .toMutableList()

    val rootTargetModules = TargetModules.all(modules)
    val rootNode = DefaultMutableTreeNode(rootTargetModules)
        .appendChildren(sortedModules)

    return DefaultTreeModel(rootNode)
}

private fun DefaultMutableTreeNode.appendChildren(
    sortedModules: List<ModuleModel>
): DefaultMutableTreeNode {
    val childModules = when (val nodeTargetModules = userObject as TargetModules) {
        is TargetModules.None -> emptyList()
        is TargetModules.One -> {
            sortedModules.filter { nodeTargetModules.module.projectModule == it.projectModule.parent }
        }
        is TargetModules.All -> {
            sortedModules.filter { module -> module.projectModule.parent == null }
        }
    }

    for (childModule in childModules) {
        val nodeTargetModules = TargetModules.One(childModule)
        val childNode = DefaultMutableTreeNode(nodeTargetModules)
        add(childNode)

        childNode.appendChildren(sortedModules)
    }

    return this
}

private fun DefaultMutableTreeNode.findPathWithData(currentTargetModules: TargetModules): TreePath? {
    if (targetModulesOrNull() == currentTargetModules) {
        return TreePath(path)
    }

    return children().asSequence()
        .filterIsInstance<DefaultMutableTreeNode>()
        .mapNotNull { it.findPathWithData(currentTargetModules) }
        .firstOrNull()
}

private fun DefaultMutableTreeNode.targetModulesOrNull() = userObject as? TargetModules
