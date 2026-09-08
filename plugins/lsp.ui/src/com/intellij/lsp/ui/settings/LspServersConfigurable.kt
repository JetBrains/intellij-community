// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.ui.settings

import com.intellij.ide.DataManager
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.lsp.ui.ConfigurableLspIntegrationProvider
import com.intellij.lsp.ui.LspUiBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.impl.LspPluginServerConfiguration
import com.intellij.platform.lsp.impl.LspServerSettingsProvider
import com.intellij.ui.EditorNotifications
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.tree.TreeNode

private const val LSP_PLUGIN_SEARCH_TAG = "/tag:\"Language Server\""

internal class LspServersConfigurable(private val project: Project) : MasterDetailsComponent(), SearchableConfigurable, Disposable {
  private val settings = LspServerSettings.getInstance(project)
  private val morePluginsLinkPanel = NonOpaquePanel(BorderLayout()).apply {
    border = JBUI.Borders.empty(8, 20, 8, 0)
    add(ActionLink(LspUiBundle.message("lsp.settings.more.plugins")) { showMorePlugins() }, BorderLayout.WEST)
  }

  init {
    initTree()
  }

  override fun getId(): String = "lsp.servers"

  override fun getDisplayName(): String = LspUiBundle.message("lsp.settings.name")

  override fun getEmptySelectionString(): String = LspUiBundle.message("lsp.settings.empty.selection")

  override fun reInitWholePanelIfNeeded() {
    super.reInitWholePanelIfNeeded()

    val leftPanel = splitter.firstComponent
    if (morePluginsLinkPanel.parent !== leftPanel) {
      leftPanel.add(morePluginsLinkPanel, BorderLayout.SOUTH)
    }
  }

  private fun showMorePlugins() {
    val allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(myTree))
    if (allSettings == null) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, PluginManagerConfigurable::class.java) {
        it.enableSearch(LSP_PLUGIN_SEARCH_TAG)
      }
    }
    else {
      allSettings.select(allSettings.find(PluginManagerConfigurable.ID), LSP_PLUGIN_SEARCH_TAG)
    }
  }

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
      e.presentation.isEnabled = selectedNode?.configurable is LspServerNamedConfigurable
    }
  }

  override fun initUi() {
    addServerNodes()
    super.initUi()
  }

  private fun addServerNodes() {
    for (server in settings.servers) {
      addServerNode(server.copy())
    }
    LspServerSettingsProvider.EP_NAME.processWithPluginDescriptor { provider, pluginDescriptor ->
      addPluginServerNode(provider, settings.getPluginConfiguration(provider), pluginDescriptor)
    }
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
    val configurable = LspServerNamedConfigurable(project, config, TREE_UPDATER)
    val node = MyNode(configurable)
    Disposer.register(this, configurable)
    addNode(node, myRoot)
    return node
  }

  private fun addPluginServerNode(
    provider: LspServerSettingsProvider,
    configuration: LspPluginServerConfiguration,
    pluginDescriptor: PluginDescriptor,
  ): MyNode {
    val configurable = PluginLspServerNamedConfigurable(project, provider, configuration, pluginDescriptor, TREE_UPDATER)
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
    val pluginConfigurables = mutableListOf<PluginLspServerNamedConfigurable>()
    processNodes { node ->
      when (val configurable = node.configurable) {
        is LspServerNamedConfigurable -> newServers.add(configurable.editableObject)
        is PluginLspServerNamedConfigurable -> pluginConfigurables.add(configurable)
      }
      true
    }

    val duplicateNames = newServers.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
    if (duplicateNames.isNotEmpty()) {
      throw ConfigurationException(LspUiBundle.message("lsp.settings.error.duplicate.names", duplicateNames.joinToString(", ")))
    }

    val providersToRestart = linkedSetOf<Class<out LspIntegrationProvider>>()
    for (configurable in pluginConfigurables) {
      val providerClass = configurable.applyConfiguration()
      if (providerClass != null) {
        providersToRestart.add(providerClass)
      }
    }

    settings.servers.clear()
    settings.servers.addAll(newServers)
    if (!project.isDefault) {
      LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(ConfigurableLspIntegrationProvider::class.java)
      providersToRestart.forEach { providerClass ->
        LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(providerClass)
      }
      EditorNotifications.getInstance(project).updateAllNotifications()
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
    addServerNodes()

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
    private val project: Project,
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
        serverConfigurable = LspServerConfigurable(project, serverConfiguration)
      }
      return serverConfigurable!!
    }
  }
}