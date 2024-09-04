// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.NamedColorUtil
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.inspections.PyInterpreterInspection
import com.jetbrains.python.packaging.toolwindow.details.PyPackageInfoPanel
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.InstalledPackage
import com.jetbrains.python.packaging.toolwindow.model.PyPackagesViewData
import com.jetbrains.python.packaging.toolwindow.modules.PyPackagesModuleController
import com.jetbrains.python.packaging.toolwindow.packages.PyPackageSearchTextField
import com.jetbrains.python.packaging.toolwindow.packages.PyPackagesListController
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

class PyPackagingToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(false, true), Disposable {
  private val moduleController = PyPackagesModuleController(project).also {
    Disposer.register(this, it)
  }

  private val packageListController = PyPackagesListController(project, controller = this).also {
    Disposer.register(this, it)
  }

  private val descriptionController = PyPackageInfoPanel(project).also {
    Disposer.register(this, it)
  }


  private val packagingScope = PyPackageCoroutine.getIoScope(project)

  private val searchTextField: SearchTextField = PyPackageSearchTextField(project)


  // layout
  private var mainPanel: JPanel? = null
  private var splitter: OnePixelSplitter? = null
  private var leftPanel: JComponent
  private val rightPanel: JComponent = descriptionController.component

  internal var contentVisible: Boolean
    get() = mainPanel!!.isVisible
    set(value) {
      mainPanel!!.isVisible = value
    }


  init {
    val service = project.service<PyPackagingToolWindowService>()
    Disposer.register(service, this)
    withEmptyText(message("python.toolwindow.packages.no.interpreter.text"))

    @Suppress("DialogTitleCapitalization")
    emptyText.appendLine(message("python.sdk.popup.interpreter.settings"), SimpleTextAttributes.LINK_ATTRIBUTES, object : ActionListener {
      override fun actionPerformed(e: ActionEvent?) {
        PyInterpreterInspection.InterpreterSettingsQuickFix.showPythonInterpreterSettings(project, null)
      }
    })
    leftPanel = createLeftPanel()



    initOrientation(service, true)
    trackOrientation(service)
  }


  override fun uiDataSnapshot(sink: DataSink) {
    sink[PyPackagesUiComponents.SELECTED_PACKAGE_DATA_CONTEXT] = descriptionController.getPackage()
    sink[PyPackagesUiComponents.SELECTED_PACKAGES_DATA_CONTEXT] = this.packageListController.getSelectedPackages()
    super.uiDataSnapshot(sink)
  }

  fun getSelectedPackage(): DisplayablePackage? = descriptionController.getPackage()

  private fun initOrientation(service: PyPackagingToolWindowService, horizontal: Boolean) {
    val proportionKey = if (horizontal) HORIZONTAL_SPLITTER_KEY else VERTICAL_SPLITTER_KEY
    splitter = OnePixelSplitter(!horizontal, proportionKey, 0.3f).apply {
      firstComponent = leftPanel
      secondComponent = rightPanel
    }

    val actionGroup = DefaultActionGroup()
    val collapseAllAction = DumbAwareAction.create(message("python.toolwindow.packages.collapse.all.action"), AllIcons.Actions.Collapseall) {
      packageListController.collapseAll()
    }
    actionGroup.add(collapseAllAction)

    val reloadReposAction = DumbAwareAction.create(message("python.toolwindow.packages.reload.repositories.action"), AllIcons.Actions.Refresh) {
      startLoadingSdk()
      moduleController.rebuildList()
      service.reloadPackages()
    }
    actionGroup.add(reloadReposAction)

    actionGroup.add(ActionManager.getInstance().getAction("PyPackageToolbarAdditional"))
    actionGroup.isPopup = false
    val actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, actionGroup, true)
    actionToolbar.targetComponent = this
    actionToolbar.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY

    mainPanel = PyPackagesUiComponents.borderPanel {
      val topToolbar = PyPackagesUiComponents.boxPanel {
        border = SideBorder(NamedColorUtil.getBoundsColor(), SideBorder.BOTTOM)
        preferredSize = Dimension(preferredSize.width, 30)
        minimumSize = Dimension(minimumSize.width, 30)
        maximumSize = Dimension(maximumSize.width, 30)
        add(searchTextField)
        //actionToolbar.component.maximumSize = Dimension(70, actionToolbar.component.maximumSize.height)
        add(actionToolbar.component)
      }
      add(topToolbar, BorderLayout.NORTH)
      add(splitter!!, BorderLayout.CENTER)
    }
    setContent(mainPanel!!)
  }

  private fun createLeftPanel(): JComponent {
    if (project.modules.size == 1)
      return packageListController.component

    val left = JPanel(BorderLayout()).apply {
      border = BorderFactory.createEmptyBorder()
    }

    val splitter = OnePixelSplitter(false).apply {
      firstComponent = moduleController.component
      secondComponent = packageListController.component
    }
    splitter.proportion = 0.2f
    left.add(splitter, BorderLayout.CENTER)

    return left
  }


  private fun trackOrientation(service: PyPackagingToolWindowService) {
    service.project.messageBus.connect(service).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      var myHorizontal = true
      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val toolWindow = toolWindowManager.getToolWindow("Python Packages")
        if (toolWindow == null || toolWindow.isDisposed) return
        val isHorizontal = toolWindow.anchor.isHorizontal

        if (myHorizontal != isHorizontal) {
          myHorizontal = isHorizontal
          val content = toolWindow.contentManager.contents.find { it?.component is PyPackagingToolWindowPanel }
          val panel = content?.component as? PyPackagingToolWindowPanel ?: return
          panel.initOrientation(service, myHorizontal)
        }
      }
    })
  }

  fun packageSelected(selectedPackage: DisplayablePackage?) {
    descriptionController.setPackage(selectedPackage)
  }

  fun showSearchResult(installed: List<InstalledPackage>, repoData: List<PyPackagesViewData>) {
    packageListController.showSearchResult(installed, repoData)
  }

  fun resetSearch(installed: List<InstalledPackage>, repos: List<PyPackagesViewData>, currentSdk: Sdk?) {
    packageListController.resetSearch(installed, repos, currentSdk)
  }


  fun setEmpty() {
    packageSelected(null)
  }

  override fun dispose() {
    packagingScope.cancel()
  }

  internal suspend fun recreateModulePanel() {
    val newPanel = createLeftPanel()
    withContext(Dispatchers.Main) {
      leftPanel = newPanel
      splitter?.firstComponent = leftPanel
      splitter?.repaint()
    }
  }

  fun selectPackageName(name: String) {
    this.packageListController.selectPackage(name)
  }

  fun startLoadingSdk() {
    this.descriptionController.setPackage(null)
    packageListController.startSdkInit()
  }

  companion object {
    private const val HORIZONTAL_SPLITTER_KEY = "Python.PackagingToolWindow.Horizontal"
    private const val VERTICAL_SPLITTER_KEY = "Python.PackagingToolWindow.Vertical"
  }
}

