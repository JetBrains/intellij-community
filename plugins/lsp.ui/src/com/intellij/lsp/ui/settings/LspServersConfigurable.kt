// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.ui.settings

import com.intellij.lsp.ui.ConfigurableLspIntegrationProvider
import com.intellij.lsp.ui.LspUiBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.IconUtil
import javax.swing.tree.TreeNode

internal class LspServersConfigurable(private val project: Project) : MasterDetailsComponent(), SearchableConfigurable, Disposable {
  private val settings = LspServerSettings.getInstance(project)

  init {
    initTree()
  }

  override fun getId(): String = "lsp.servers"

  override fun getDisplayName(): String = LspUiBundle.message("lsp.settings.name")

  override fun getEmptySelectionString(): String = LspUiBundle.message("lsp.settings.empty.selection")

  override fun createActions(fromPopup: Boolean): List<AnAction> = listOf(AddAction(), MyDeleteAction())

  private inner class AddAction : DumbAwareAction(LspUiBundle.message("lsp.settings.action.add"), LspUiBundle.message("lsp.settings.action.add.description"), IconUtil.addIcon) {
    override fun actionPerformed(e: AnActionEvent) {
      val newConfig = LspServerConfiguration(
        name = generateUniqueName(),
        arguments = "--stdio"
      )
      val node = addServerNode(newConfig)
      selectNodeInTree(node)
    }
  }

  private inner class MyDeleteAction : DumbAwareAction(LspUiBundle.message("lsp.settings.action.delete"), LspUiBundle.message("lsp.settings.action.delete.description"), IconUtil.removeIcon) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
      val selected = selectedNode ?: return
      val configurable = selected.configurable as? LspServerNamedConfigurable ?: return
      val serverName = configurable.displayName

      val result = Messages.showYesNoDialog(
        project,
        LspUiBundle.message("lsp.settings.dialog.remove.server.message", serverName),
        LspUiBundle.message("lsp.settings.dialog.remove.server.title"),
        Messages.getQuestionIcon()
      )

      if (result == Messages.YES) {
        val selectionPath = myTree.selectionPath ?: return
        removePaths(selectionPath)
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = selectedNode != null
    }
  }

  override fun initUi() {
    settings.servers.forEach { server ->
      val serverCopy = server.copy()
      addServerNode(serverCopy)
    }
    super.initUi()
  }

  private fun generateUniqueName(): String {
    val baseName = LspUiBundle.message("lsp.settings.default.name")
    val existingNames = settings.servers.map { it.name }.toSet()
    var counter = 1
    var name = baseName
    while (existingNames.contains(name)) {
      name = "$baseName $counter"
      counter++
    }
    return name
  }

  private fun addServerNode(config: LspServerConfiguration): MyNode {
    val configurable = LspServerNamedConfigurable(config, TREE_UPDATER)
    val node = MyNode(configurable)
    Disposer.register(this, configurable)
    addNode(node, myRoot)
    return node
  }

  override fun wasObjectStored(editableObject: Any?): Boolean {
    if (editableObject is LspServerConfiguration) {
      return settings.servers.contains(editableObject)
    }
    return false
  }


  @Throws(ConfigurationException::class)
  override fun apply() {
    super.apply()

    val newServers = mutableListOf<LspServerConfiguration>()
    processNodes { node ->
      if (node.configurable is LspServerNamedConfigurable) {
        val config = (node.configurable as LspServerNamedConfigurable).editableObject
        newServers.add(config)
      }
      true
    }

    val duplicateNames = newServers.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
    if (duplicateNames.isNotEmpty()) {
      throw ConfigurationException(LspUiBundle.message("lsp.settings.error.duplicate.names", duplicateNames.joinToString(", ")))
    }

    settings.servers.clear()
    settings.servers.addAll(newServers)
    if (!project.isDefault) {
      LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(ConfigurableLspIntegrationProvider::class.java)
    }
  }

  override fun isModified(): Boolean {
    if (super<MasterDetailsComponent>.isModified()) return true

    val currentConfigs = mutableListOf<LspServerConfiguration>()
    processNodes { node ->
      if (node.configurable is LspServerNamedConfigurable) {
        val config = (node.configurable as LspServerNamedConfigurable).editableObject
        currentConfigs.add(config)
      }
      true
    }

    if (currentConfigs.size != settings.servers.size) return true

    return !currentConfigs.zip(settings.servers).all { (current, original) ->
      current == original
    }
  }

  override fun reset() {
    myRoot.removeAllChildren()

    for (server in settings.servers) {
      val serverCopy = server.copy()
      addServerNode(serverCopy)
    }

    super<MasterDetailsComponent>.reset()
  }

  override fun dispose() {
  }

  override fun disposeUIResources() {
    super<MasterDetailsComponent>.disposeUIResources()
    Disposer.dispose(this)
  }

  private fun processNodes(processor: (MyNode) -> Boolean) {
    val root = myTree.model.root as TreeNode
    for (i in 0 until root.childCount) {
      val node = root.getChildAt(i) as MyNode
      if (!processor(node)) break
    }
  }

  private class LspServerNamedConfigurable(
    private val serverConfiguration: LspServerConfiguration,
    private val updateTree: Runnable,
  ) : NamedConfigurable<LspServerConfiguration>(), Disposable {

    private var serverConfigurable: LspServerConfigurable? = null

    override fun setDisplayName(name: String) {
      serverConfiguration.name = name
    }

    override fun getEditableObject(): LspServerConfiguration = serverConfiguration

    override fun getBannerSlogan(): String = serverConfiguration.name

    override fun createOptionsPanel() = JBScrollPane(getServerConfigurable().createComponent())

    override fun getDisplayName(): String = serverConfiguration.name.ifEmpty { LspUiBundle.message("lsp.settings.default.name") }

    override fun apply() {
      getServerConfigurable().apply()
      updateTree.run()
    }

    override fun isModified(): Boolean {
      return getServerConfigurable().isModified
    }

    override fun reset() {
      getServerConfigurable().reset()
    }

    override fun dispose() {
      serverConfigurable?.disposeUIResources()
      serverConfigurable = null
    }

    private fun getServerConfigurable(): LspServerConfigurable {
      if (serverConfigurable == null) {
        serverConfigurable = LspServerConfigurable(serverConfiguration)
      }
      return serverConfigurable!!
    }
  }
}