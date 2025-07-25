// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.asDisposable
import com.intellij.util.ui.NamedColorUtil
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.inspections.PyInterpreterInspection
import com.jetbrains.python.packaging.toolwindow.details.PyPackageInfoPanel
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.PyPackagesViewData
import com.jetbrains.python.packaging.toolwindow.modules.PyPackagesSdkController
import com.jetbrains.python.packaging.toolwindow.packages.PyPackageSearchTextField
import com.jetbrains.python.packaging.toolwindow.packages.PyPackagesListController
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
class PyPackagingToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(false, true), Disposable {

  private val packageSearchController = PyPackageSearchTextField(project)
  internal val packageListController = PyPackagesListController(project, controller = this)
  private val moduleController = PyPackagesSdkController(project)
  private val descriptionController = PyPackageInfoPanel(project)
  private val packagingScope = PyPackageCoroutine.getScope(project).childScope("Packaging tool window").also {
    Disposer.register(this, it.asDisposable())
  }

  private lateinit var contentPanel: JPanel
  private lateinit var contentSplitter: OnePixelSplitter
  private var leftPanel: JComponent = createLeftPanel()
  private val rightPanel: JComponent = descriptionController.component

  internal var contentVisible: Boolean
    get() = contentPanel.isVisible
    set(value) {
      contentPanel.isVisible = value
    }

  init {
    val service = project.service<PyPackagingToolWindowService>()
    setupEmptyText()
    initOrientation(service, true)
    trackOrientation(service)
    trackModules()
    registerDisposables(service)
  }

  private fun setupEmptyText() {
    withEmptyText(message("python.toolwindow.packages.no.interpreter.text"))
    @Suppress("DialogTitleCapitalization")
    emptyText.appendLine(message("python.sdk.popup.interpreter.settings"), SimpleTextAttributes.LINK_ATTRIBUTES, object : ActionListener {
      override fun actionPerformed(e: ActionEvent?) {
        PyInterpreterInspection.InterpreterSettingsQuickFix.showPythonInterpreterSettings(project, null)
      }
    })
  }

  private fun registerDisposables(service: PyPackagingToolWindowService) {
    Disposer.register(this, packageListController)
    Disposer.register(this, moduleController)
    Disposer.register(this, descriptionController)
    Disposer.register(service, this)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PyPackagesUiComponents.SELECTED_PACKAGE_DATA_CONTEXT] = descriptionController.getPackage()
    sink[PyPackagesUiComponents.SELECTED_PACKAGES_DATA_CONTEXT] = this.packageListController.getSelectedPackages()
    super.uiDataSnapshot(sink)
  }

  fun getSelectedPackage(): DisplayablePackage? = descriptionController.getPackage()

  private fun initOrientation(service: PyPackagingToolWindowService, horizontal: Boolean) {
    val proportionKey = if (horizontal) HORIZONTAL_SPLITTER_KEY else VERTICAL_SPLITTER_KEY
    contentSplitter = OnePixelSplitter(!horizontal, proportionKey, 0.3f).apply {
      firstComponent = leftPanel
      secondComponent = rightPanel
    }
    contentPanel = PyPackagesUiComponents.borderPanel {
      add(contentSplitter, BorderLayout.CENTER)
    }
    setContent(contentPanel)
    createAndAttachToolbar(service)
  }

  private fun createAndAttachToolbar(service: PyPackagingToolWindowService) {
    ActionManager.getInstance().createActionToolbar(
      ActionPlaces.TOOLWINDOW_CONTENT,
      createActionGroup(service),
      true
    ).apply {
      targetComponent = this@PyPackagingToolWindowPanel
      layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
    }
  }

  private fun createActionGroup(service: PyPackagingToolWindowService): DefaultActionGroup {
    return DefaultActionGroup().apply {
      add(DumbAwareAction.create(message("python.toolwindow.packages.collapse.all.action"), AllIcons.Actions.Collapseall) {
        packageListController.collapseAll()
      })
      add(DumbAwareAction.create(message("python.toolwindow.packages.reload.repositories.action"), AllIcons.Actions.Refresh) {
        startLoadingSdk()
        service.reloadPackages()
      })
      add(ActionManager.getInstance().getAction(ADDITIONAL_PACKAGE_TOOLBAR_ACTION_ID))
    }
  }

  private fun createLeftPanel(): JComponent {
    val topToolbar = createTopToolbar()
    val leftPanel = JPanel(BorderLayout()).apply {
      add(topToolbar, BorderLayout.NORTH)
      add(packageListController.component, BorderLayout.CENTER)
    }

    if (project.modules.mapNotNull { it.pythonSdk }.distinct().size <= 1) {
      return leftPanel
    }

    return JPanel(BorderLayout()).apply {
      border = BorderFactory.createEmptyBorder()
      add(OnePixelSplitter(false).apply {
        firstComponent = moduleController.mainScrollPane
        secondComponent = leftPanel
        proportion = 0.2f
      }, BorderLayout.CENTER)
    }
  }

  private fun createTopToolbar(): JComponent {
    val actionToolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.TOOLWINDOW_CONTENT,
      createActionGroup(project.service()),
      true
    ).apply {
      targetComponent = this@PyPackagingToolWindowPanel
    }

    return PyPackagesUiComponents.boxPanel {
      border = SideBorder(NamedColorUtil.getBoundsColor(), SideBorder.BOTTOM)
      preferredSize = Dimension(preferredSize.width, 30)
      minimumSize = Dimension(minimumSize.width, 30)
      maximumSize = Dimension(maximumSize.width, 30)
      add(packageSearchController)
      add(actionToolbar.component)
    }
  }

  private fun trackOrientation(service: PyPackagingToolWindowService) {
    service.project.messageBus.connect(service).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      private var isHorizontal = true

      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val toolWindow = toolWindowManager.getToolWindow(TOOLWINDOW_ID) ?: return
        if (!toolWindow.isDisposed && isHorizontal != toolWindow.anchor.isHorizontal) {
          isHorizontal = toolWindow.anchor.isHorizontal
          initOrientation(service, isHorizontal)
        }
      }
    })
  }

  private fun trackModules() {
    project.messageBus.connect(moduleController).subscribe(ModuleListener.TOPIC, object : ModuleListener {
      override fun modulesAdded(project: Project, modules: List<Module?>) = recreateModulePanel()

      override fun moduleRemoved(project: Project, module: Module) = recreateModulePanel()
    })
  }

  /**
   * Rebuilds the module list and updates the UI by creating a new left panel.
   * The updated left panel replaces the first component of the splitter, which
   * displays the list of modules.
   * Meanwhile, the second component of the splitter,
   * such as the description or details panel, remains unchanged.
   *
   * <p>The splitter is a UI component used to divide the interface into two resizable
   * sections: the left panel and the right panel.</p>
   */
  private fun recreateModulePanel() {
    moduleController.refreshModuleListAndSelection()
    val newPanel = createLeftPanel()
    PyPackagingToolWindowService.getInstance(project).serviceScope.launch {
      withContext(Dispatchers.EDT) {
        leftPanel = newPanel
        contentSplitter.firstComponent = leftPanel
        contentSplitter.repaint()
      }
    }
  }

  fun packageSelected(selectedPackage: DisplayablePackage?) {
    descriptionController.setPackage(selectedPackage)
  }

  fun showSearchResult(installed: List<DisplayablePackage>, repoData: List<PyPackagesViewData>) {
    packageListController.showSearchResult(installed, repoData)
  }

  fun resetSearch(installed: List<InstalledPackage>, repos: List<PyPackagesViewData>, currentSdk: Sdk?) {
    packageListController.resetSearch(installed, repos, currentSdk)
  }

  fun setEmpty() {
    packageSelected(null)
    packageListController.setLoadingState(false)
  }

  fun selectPackageName(name: String) {
    this.packageListController.selectPackage(name)
  }

  fun startLoadingSdk() {
    this.descriptionController.setPackage(null)
    packageListController.startSdkInit()
  }

  override fun dispose() {
    packagingScope.cancel()
  }

  companion object {
    private const val TOOLWINDOW_ID = "Python Packages"
    private const val ADDITIONAL_PACKAGE_TOOLBAR_ACTION_ID = "PyPackageToolbarAdditional"
    private const val HORIZONTAL_SPLITTER_KEY = "Python.PackagingToolWindow.Horizontal"
    private const val VERTICAL_SPLITTER_KEY = "Python.PackagingToolWindow.Vertical"
  }
}