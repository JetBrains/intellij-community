// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.ui.treeStructure.SimpleTreeStructure
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.model.MavenProfileKind
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator
import org.jetbrains.idea.maven.project.MavenPluginWithArtifact
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenIndexUpdateState
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager
import org.jetbrains.idea.maven.tasks.MavenTasksManager
import org.jetbrains.idea.maven.utils.MavenArtifactUtil.readPluginInfo
import org.jetbrains.idea.maven.utils.MavenUIUtil
import org.jetbrains.idea.maven.utils.MavenUIUtil.CheckBoxState
import org.jetbrains.idea.maven.utils.MavenUIUtil.CheckboxHandler
import org.jetbrains.idea.maven.utils.MavenUtil
import java.awt.event.InputEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import javax.swing.tree.TreePath
import kotlin.concurrent.Volatile

class MavenProjectsStructure(
  val project: Project,
  val displayMode: MavenStructureDisplayMode,
  val projectsManager: MavenProjectsManager,
  val tasksManager: MavenTasksManager,
  val shortcutsManager: MavenShortcutsManager,
  val projectsNavigator: MavenProjectsNavigator,
  tree: SimpleTree,
) : SimpleTreeStructure() {
  enum class MavenStructureDisplayMode {
    SHOW_ALL, SHOW_PROJECTS, SHOW_GOALS
  }

  private val myRoot: RootNode = RootNode(this)
  private val myModel: StructureTreeModel<MavenProjectsStructure?>
  private val myTree: SimpleTree

  @Volatile
  private var isUnloading = false

  private val myProjectToNodeMapping: MutableMap<MavenProject, ProjectNode> = ConcurrentHashMap<MavenProject, ProjectNode>()

  init {
    project.getMessageBus().simpleConnect()
      .subscribe<DynamicPluginListener>(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
        override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
          if (MavenUtil.INTELLIJ_PLUGIN_ID == pluginDescriptor.getPluginId().idString) {
            isUnloading = true
          }
        }
      })

    myTree = tree
    configureTree(myTree)
    myModel = StructureTreeModel<MavenProjectsStructure?>(this, projectsNavigator)
    tree.setModel(AsyncTreeModel(myModel, projectsNavigator))
  }

  override fun getRootElement(): RootNode {
    return myRoot
  }

  fun update() {
    val projects = this.projectsManager.getProjects()
    val deleted: MutableSet<MavenProject?> = ConcurrentHashMap.newKeySet<MavenProject?>()
    deleted.addAll(myProjectToNodeMapping.keys)
    projects.forEach(Consumer { o: MavenProject? -> deleted.remove(o) })
    doUpdateProjects(projects, deleted)
  }

  fun updateFrom(node: SimpleNode?) {
    if (node != null) {
      myModel.invalidate(node, true)
    }
  }

  fun updateUpTo(node: SimpleNode?) {
    var each = node
    while (each != null) {
      updateFrom(each)
      each = each.parent
    }
  }

  fun updateProjects(updated: MutableList<MavenProject>, deleted: MutableCollection<MavenProject?>) {
    doUpdateProjects(updated, deleted)
  }

  private fun doUpdateProjects(updated: MutableList<MavenProject>, deleted: MutableCollection<MavenProject?>) {
    for (each in updated) {
      var node = findNodeFor(each)
      if (node == null) {
        node = ProjectNode(this, each)
        myProjectToNodeMapping[each] = node
      }
      doUpdateProject(node)
    }

    for (each in deleted) {
      val node = myProjectToNodeMapping.remove(each)
      if (node != null) {
        val parent = node.getGroup()
        parent.remove(node)
      }
    }

    myRoot.updateProfiles()
  }

  private fun doUpdateProject(node: ProjectNode) {
    val project = node.getMavenProject()

    var newParentNode: ProjectsGroupNode? = myRoot

    if (this.projectsNavigator.groupModules) {
      val aggregator = this.projectsManager.findAggregator(project)
      if (aggregator != null) {
        val aggregatorNode = findNodeFor(aggregator)
        if (aggregatorNode != null && aggregatorNode.isVisible()) {
          newParentNode = aggregatorNode
        }
      }
    }

    node.updateProject()
    reconnectNode(node, newParentNode!!)

    val newModulesParentNode = if (this.projectsNavigator.groupModules && node.isVisible()) node else myRoot
    for (each in this.projectsManager.getModules(project)) {
      val moduleNode = findNodeFor(each)
      if (moduleNode != null && moduleNode.getParent() != newModulesParentNode) {
        reconnectNode(moduleNode, newModulesParentNode)
      }
    }
  }

  fun updateProfiles() {
    myRoot.updateProfiles()
  }

  fun updateIgnored(projects: List<MavenProject?>) {
    for (each in projects) {
      val node = findNodeFor(each)
      if (node == null) continue
      node.updateIgnored()
    }
  }

  fun accept(visitor: TreeVisitor) {
    (myTree.getModel() as TreeVisitor.Acceptor).accept(visitor)
  }

  fun updateGoals() {
    for (each in myProjectToNodeMapping.values) {
      each.updateGoals()
    }
  }

  fun updateRunConfigurations() {
    for (each in myProjectToNodeMapping.values) {
      each.updateRunConfigurations()
    }
  }

  fun select(project: MavenProject?) {
    val node = findNodeFor(project)
    if (node != null) select(node)
  }

  fun select(node: SimpleNode) {
    myModel.select(node, myTree, Consumer { `_`: TreePath? -> })
  }

  private fun findNodeFor(project: MavenProject?): ProjectNode? {
    return myProjectToNodeMapping.get(project)
  }

  @ApiStatus.Internal
  enum class DisplayKind {
    ALWAYS, NEVER, NORMAL
  }

  fun showOnlyBasicPhases(): Boolean {
    if (this.displayMode == MavenStructureDisplayMode.SHOW_GOALS) {
      return false
    }
    return this.projectsNavigator.showBasicPhasesOnly
  }

  enum class ErrorLevel {
    NONE, ERROR
  }

  fun updatePluginsTree(pluginsNode: PluginsNode, pluginInfos: MutableList<MavenPluginWithArtifact>) {
    UpdatePluginsTreeTask(pluginsNode, pluginInfos).run()
  }

  private inner class UpdatePluginsTreeTask(
    private val myParentNode: PluginsNode,
    private val myPluginInfos: MutableList<MavenPluginWithArtifact>,
  ) : Runnable {
    override fun run() {
      val pluginNodes: MutableList<PluginNode?> = ArrayList<PluginNode?>()
      val iterator = myPluginInfos.iterator()
      while (!isUnloading && iterator.hasNext()) {
        val next = iterator.next()
        val pluginInfo = readPluginInfo(next.artifact)
        pluginNodes.add(PluginNode(this@MavenProjectsStructure, myParentNode, next.plugin, pluginInfo))
      }
      myParentNode.pluginNodes.clear()
      if (isUnloading) return
      myParentNode.getPluginNodes().addAll(pluginNodes)
      myParentNode.sort(myParentNode.pluginNodes)
      myParentNode.childrenChanged()
    }
  }

  fun updateRepositoryStatus(state: MavenIndexUpdateState) {
    myProjectToNodeMapping.values.forEach(Consumer { pn: ProjectNode? ->
      pn!!.getRepositoriesNode().updateStatus(state)
    })
  }

  companion object {
    private fun configureTree(tree: SimpleTree) {
      tree.setRootVisible(false)
      tree.setShowsRootHandles(true)

      MavenUIUtil.installCheckboxRenderer(tree, object : CheckboxHandler {
        override fun toggle(treePath: TreePath?, e: InputEvent?) {
          val node = tree.getNodeFor(treePath)
          if (node != null) {
            node.handleDoubleClickOrEnter(tree, e)
          }
        }

        override fun isVisible(userObject: Any?): Boolean {
          return userObject is ProfileNode
        }

        override fun getState(userObject: Any): CheckBoxState {
          val state = (userObject as ProfileNode).getState()
          return when (state) {
            MavenProfileKind.NONE -> CheckBoxState.UNCHECKED
            MavenProfileKind.EXPLICIT -> CheckBoxState.CHECKED
            MavenProfileKind.IMPLICIT -> CheckBoxState.PARTIAL
          }
        }
      })
    }

    private fun reconnectNode(node: ProjectNode, newParentNode: ProjectsGroupNode) {
      val oldParentNode = node.getGroup()
      if (oldParentNode == null || oldParentNode != newParentNode) {
        if (oldParentNode != null) {
          oldParentNode.remove(node)
        }
        newParentNode.add(node)
      }
      else {
        newParentNode.sortProjects()
      }
    }

    @JvmStatic
    fun <T : MavenSimpleNode?> getSelectedNodes(tree: SimpleTree, nodeClass: Class<T?>?): MutableList<T?> {
      val filtered: MutableList<T?> = ArrayList<T?>()
      for (node in getSelectedNodes(tree)) {
        if ((nodeClass != null) && (!nodeClass.isInstance(node))) {
          filtered.clear()
          break
        }
        filtered.add(node as T?)
      }
      return filtered
    }

    private fun getSelectedNodes(tree: SimpleTree): MutableList<SimpleNode?> {
      val nodes: MutableList<SimpleNode?> = ArrayList<SimpleNode?>()
      val treePaths = tree.getSelectionPaths()
      if (treePaths != null) {
        for (treePath in treePaths) {
          nodes.add(tree.getNodeFor(treePath))
        }
      }
      return nodes
    }
  }
}