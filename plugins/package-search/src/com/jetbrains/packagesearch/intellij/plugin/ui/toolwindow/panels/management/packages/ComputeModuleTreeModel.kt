package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

internal data class ModulesTreeData(
    val treeModel: TreeModel,
    val selectedPath: TreePath
)

internal fun computeModuleTreeModel(
    modules: List<ModuleModel>,
    currentTargetModules: TargetModules,
    traceInfo: TraceInfo?
): ModulesTreeData {
    if (modules.isEmpty()) {
        logDebug(traceInfo, "computeModuleTreeModel()") { "No modules to display, setting target to None" }
        val rootNode = DefaultMutableTreeNode(TargetModules.None)
        return ModulesTreeData(treeModel = DefaultTreeModel(rootNode), selectedPath = TreePath(rootNode))
    }

    logDebug(traceInfo, "computeModuleTreeModel()") { "Calculating tree" }
    val sortedModules = modules.sortedBy { it.projectModule.name }
        .toMutableList()

    val rootTargetModules = TargetModules.all(modules)
    val rootNode = DefaultMutableTreeNode(rootTargetModules)
        .appendChildren(sortedModules, currentTargetModules)

    logDebug(traceInfo, "computeModuleTreeModel()") { "Calculating selection path" }
    val selectionPath = rootNode.findPathWithData(currentTargetModules) ?: TreePath(rootNode)

    return ModulesTreeData(DefaultTreeModel(rootNode), selectionPath)
}

private fun DefaultMutableTreeNode.appendChildren(
    sortedModules: List<ModuleModel>,
    currentTargetModules: TargetModules
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

        childNode.appendChildren(sortedModules, currentTargetModules)
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
