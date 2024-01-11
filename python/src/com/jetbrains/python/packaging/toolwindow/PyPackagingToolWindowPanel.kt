// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.util.Alarm
import com.intellij.util.Alarm.ThreadToUse
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.common.PythonLocalPackageSpecification
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonVcsPackageSpecification
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.icons.PythonIcons
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.ListSelectionListener

class PyPackagingToolWindowPanel(private val project: Project, toolWindow: ToolWindow) : SimpleToolWindowPanel(false, true), Disposable  {
  internal val packagingScope = CoroutineScope(Dispatchers.IO)
  private var selectedPackage: DisplayablePackage? = null
  private var selectedPackageDetails: PythonPackageDetails? = null

  // UI elements
  private val packageNameLabel = JLabel().apply { font = JBFont.h4().asBold(); isVisible = false }
  private val versionLabel = JLabel().apply { isVisible = false }
  private val documentationLink = HyperlinkLabel(message("python.toolwindow.packages.documentation.link")).apply {
    addHyperlinkListener { if (documentationUrl != null) BrowserUtil.browse(documentationUrl!!) }
    isVisible = false
  }

  private val searchTextField: SearchTextField
  private val searchAlarm: Alarm
  private val installButton: JBOptionButton
  private val uninstallAction: JComponent
  private val progressBar: JProgressBar
  private val versionSelector: JBComboBoxLabel
  private val descriptionPanel: JCEFHtmlPanel
  private var documentationUrl: String? = null

  private val packageListPanel: JPanel
  private val tablesView: PyPackagingTablesView
  private val noPackagePanel = JBPanelWithEmptyText().apply { emptyText.text = message("python.toolwindow.packages.description.panel.placeholder") }

  // layout
  private var mainPanel: JPanel? = null
  private var splitter: OnePixelSplitter? = null
  private var leftPanel: JComponent
  private val rightPanel: JComponent

  internal var contentVisible: Boolean
    get() = mainPanel!!.isVisible
    set(value) {
      mainPanel!!.isVisible = value
    }

  private val latestText: String
    get() = message("python.toolwindow.packages.latest.version.label")
  private val fromVcsText: String
    get() = message("python.toolwindow.packages.add.package.from.vcs")
  private val fromDiscText: String
    get() = message("python.toolwindow.packages.add.package.from.disc")


  init {
    val service = project.service<PyPackagingToolWindowService>()
    Disposer.register(service, this)
    withEmptyText(message("python.toolwindow.packages.no.interpreter.text"))
    descriptionPanel = PyPackagingJcefHtmlPanel(service.project)
    Disposer.register(toolWindow.disposable, descriptionPanel)

    versionSelector = JBComboBoxLabel().apply {
      text = latestText
      isVisible = false
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
          val versions = listOf(latestText) + (selectedPackageDetails?.availableVersions ?: emptyList())
          JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<String>(null, versions) {
              override fun onChosen(@NlsContexts.Label selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                this@apply.text = selectedValue
                return FINAL_CHOICE
              }
            }, 8).showUnderneathOf(this@apply)
        }
      })
    }

    // todo[akniazev]: add options to install with args
    installButton = JBOptionButton(null, null).apply { isVisible = false }

    val uninstallToolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, DefaultActionGroup(DefaultActionGroup().apply {
        add(object : AnAction(message("python.toolwindow.packages.delete.package")) {
          override fun actionPerformed(e: AnActionEvent) {
            if (selectedPackage is InstalledPackage) {
              packagingScope.launch(Dispatchers.Main) {
                startProgress()
                withContext(Dispatchers.IO) {
                  service.deletePackage(selectedPackage as InstalledPackage)
                }
                stopProgress()
              }
            } else error("Trying to delete package, that is not InstalledPackage")
          }
        })
        isPopup = true
        with(templatePresentation) {
          putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
          icon = AllIcons.Actions.More
        }
      }), true)
    uninstallToolbar.targetComponent = this
    uninstallAction = uninstallToolbar.component

    progressBar = JProgressBar(JProgressBar.HORIZONTAL).apply {
      maximumSize.width = 200
      minimumSize.width = 200
      preferredSize.width = 200
      isVisible = false
      isIndeterminate = true
    }

    packageListPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      alignmentX = LEFT_ALIGNMENT
      background = UIUtil.getListBackground()
    }

    tablesView = PyPackagingTablesView(project, packageListPanel, this)

    leftPanel = createLeftPanel(service)
    rightPanel = borderPanel {
      add(borderPanel {
        border = SideBorder(JBColor.GRAY, SideBorder.BOTTOM)
        preferredSize = Dimension(preferredSize.width, 50)
        minimumSize = Dimension(minimumSize.width, 50)
        maximumSize = Dimension(maximumSize.width, 50)
        add(boxPanel {
          add(Box.createHorizontalStrut(10))
          add(packageNameLabel)
          add(Box.createHorizontalStrut(10))
          add(documentationLink)
        }, BorderLayout.WEST)
        add(boxPanel {
          alignmentX = Component.RIGHT_ALIGNMENT
          add(progressBar)
          add(versionSelector)
          add(versionLabel)
          add(installButton)
          add(uninstallAction)
          add(Box.createHorizontalStrut(10))
        }, BorderLayout.EAST)
      }, BorderLayout.NORTH)
      add(descriptionPanel.component, BorderLayout.CENTER)
    }

    searchTextField = object : SearchTextField(false) {
      init {
        preferredSize = Dimension(250, 30)
        minimumSize = Dimension(250, 30)
        maximumSize = Dimension(250, 30)
        textEditor.border = JBUI.Borders.empty(0, 6, 0, 0)
        textEditor.isOpaque = true
        textEditor.emptyText.text = message("python.toolwindow.packages.search.text.placeholder")
      }

      override fun onFieldCleared() {
        service.handleSearch("")
      }
    }

    searchAlarm = SingleAlarm({
      service.handleSearch(searchTextField.text.trim())
    }, 500, service, ThreadToUse.SWING_THREAD, ModalityState.nonModal())

    searchTextField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        searchAlarm.cancelAndRequest()
      }
    })


    initOrientation(service, true)
    trackOrientation(service)
  }

  private fun initOrientation(service: PyPackagingToolWindowService, horizontal: Boolean) {
    val second = if (splitter?.secondComponent == rightPanel) rightPanel else noPackagePanel
    val proportionKey = if (horizontal) HORIZONTAL_SPLITTER_KEY else VERTICAL_SPLITTER_KEY
    splitter = OnePixelSplitter(!horizontal, proportionKey, 0.3f).apply {
      firstComponent = leftPanel
      secondComponent = second
    }

    val actionGroup = DefaultActionGroup()
    actionGroup.add(object : AnAction({ message("python.toolwindow.packages.reload.repositories.action")}, AllIcons.Actions.Refresh) {
      override fun actionPerformed(e: AnActionEvent) {
        service.reloadPackages()
      }
    })
    actionGroup.add(object : AnAction({ message("python.toolwindow.packages.manage.repositories.action") }, AllIcons.General.GearPlain) {
      override fun actionPerformed(e: AnActionEvent) {
        service.manageRepositories()
      }
    })
    val actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, actionGroup,true)
    actionToolbar.targetComponent = this


    val installFromLocationLink = DropDownLink(message("python.toolwindow.packages.add.package.action"),
                                               listOf(fromVcsText, fromDiscText)) {
      val specification = when (it) {
        fromDiscText -> showInstallFromDiscDialog(service)
        fromVcsText -> showInstallFromVcsDialog(service)
        else -> throw IllegalStateException("Unknown operation")
      }
      if (specification != null) {
        packagingScope.launch {
          service.installPackage(specification)
        }
      }
    }

    mainPanel = borderPanel {
      val topToolbar = boxPanel {
        border = SideBorder(NamedColorUtil.getBoundsColor(), SideBorder.BOTTOM)
        preferredSize = Dimension(preferredSize.width, 30)
        minimumSize = Dimension(minimumSize.width, 30)
        maximumSize = Dimension(maximumSize.width, 30)
        add(searchTextField)
        actionToolbar.component.maximumSize = Dimension(70, actionToolbar.component.maximumSize.height)
        add(actionToolbar.component)
        add(installFromLocationLink)
      }
      add(topToolbar, BorderLayout.NORTH)
      add(splitter!!, BorderLayout.CENTER)
    }
    setContent(mainPanel!!)
  }

  private fun createLeftPanel(service: PyPackagingToolWindowService): JComponent {
    if (project.modules.size == 1) return ScrollPaneFactory.createScrollPane(packageListPanel, true)

    val left = JPanel(BorderLayout()).apply {
      border = BorderFactory.createEmptyBorder()
    }

    val modulePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
      border = SideBorder(NamedColorUtil.getBoundsColor(), SideBorder.RIGHT)
      maximumSize = Dimension(80, maximumSize.height)
      minimumSize = Dimension(50, minimumSize.height)
    }

    val moduleList = JBList(ModuleManager.getInstance(project).modules.toList().sortedBy { it.name }).apply {
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      border = JBUI.Borders.empty()

      val itemRenderer = JBLabel("", PythonIcons.Python.PythonClosed, JLabel.LEFT).apply {
        border = JBUI.Borders.empty(0, 10)
      }
      cellRenderer = ListCellRenderer { _, value, _, _, _ -> itemRenderer.text = value.name; itemRenderer }

      addListSelectionListener(ListSelectionListener { e ->
        if (e.valueIsAdjusting) return@ListSelectionListener
        val selectedModule = this@apply.selectedValue
        val sdk = selectedModule.pythonSdk ?: return@ListSelectionListener
        packagingScope.launch {
          service.initForSdk(sdk)
        }
      })
    }

    val fileListener = object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        if (project.modules.size > 1) {
          val newFile = event.newFile ?: return
          val module = ModuleUtilCore.findModuleForFile(newFile, project)
          packagingScope.launch {
            val index = (moduleList.model as DefaultListModel<Module>).indexOf(module)
            moduleList.selectionModel.setSelectionInterval(index, index)
          }
        }
      }
    }
    service.project.messageBus
      .connect(service)
      .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileListener)

    modulePanel.add(moduleList)
    left.add(ScrollPaneFactory.createScrollPane(modulePanel, true), BorderLayout.WEST)
    left.add(ScrollPaneFactory.createScrollPane(packageListPanel, true), BorderLayout.CENTER)

    return left
  }

  private fun showInstallFromVcsDialog(service: PyPackagingToolWindowService): PythonVcsPackageSpecification? {
    var editable = false
    var link = ""
    val systems = listOf(message("python.toolwindow.packages.add.package.vcs.git"),
                         message("python.toolwindow.packages.add.package.vcs.svn"),
                         message("python.toolwindow.packages.add.package.vcs.hg"),
                         message("python.toolwindow.packages.add.package.vcs.bzr"))
    var vcs = systems.first()

    val panel = panel {
      row {
        comboBox(systems)
          .bindItem({ vcs }, { vcs = it!! })
        textField()
          .columns(COLUMNS_MEDIUM)
          .bindText({ link }, { link = it })
          .align(AlignX.FILL)
      }
      row {
        checkBox(message("python.toolwindow.packages.add.package.as.editable"))
          .bindSelected({ editable }, { editable = it })
      }
    }

    val shouldInstall = dialog(message("python.toolwindow.packages.add.package.dialog.title"), panel, project = service.project, resizable = true).showAndGet()
    if (shouldInstall) {
      val prefix = when (vcs) {
        message("python.toolwindow.packages.add.package.vcs.git") -> "git+"
        message("python.toolwindow.packages.add.package.vcs.svn") -> "svn+"
        message("python.toolwindow.packages.add.package.vcs.hg") -> "hg+"
        message("python.toolwindow.packages.add.package.vcs.bzr") -> "bzr+"
        else -> throw IllegalStateException("Unknown VCS")
      }

      return PythonVcsPackageSpecification(link, link, prefix, editable)
    }
    return null
  }

  private fun showInstallFromDiscDialog(service: PyPackagingToolWindowService): PythonLocalPackageSpecification? {
    var editable = false

    val textField = TextFieldWithBrowseButton().apply {
      addBrowseFolderListener(message("python.toolwindow.packages.add.package.path.selector"), "", service.project,
                              FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor())
    }
    val panel = panel {
      row(message("python.toolwindow.packages.add.package.path")) {
        cell(textField)
          .columns(COLUMNS_MEDIUM)
          .align(AlignX.FILL)
      }
      row {
        checkBox(message("python.toolwindow.packages.add.package.as.editable"))
          .bindSelected({ editable }, { editable = it })
      }
    }

    val shouldInstall = dialog(message("python.toolwindow.packages.add.package.dialog.title"), panel, project = service.project, resizable = true).showAndGet()
    return if (shouldInstall) PythonLocalPackageSpecification(textField.text, textField.text, editable) else null
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



  fun packageSelected(selectedPackage: DisplayablePackage) {
    val service = project.service<PyPackagingToolWindowService>()
    val managementEnabled = PyPackageUtil.packageManagementEnabled(service.currentSdk, true, false)
    showHeaderForPackage(selectedPackage, managementEnabled)

    this.selectedPackage = selectedPackage
    packagingScope.launch {
      val packageDetails = service.detailsForPackage(selectedPackage)

      val installActions = if (managementEnabled) {
        PythonPackagingToolwindowActionProvider.EP_NAME
          .extensionList
          .firstNotNullOf {
            it.getInstallActions(packageDetails, service.manager)
          }
      }
      else emptyList()

      withContext(Dispatchers.Main) {
        selectedPackageDetails = packageDetails

        if (splitter?.secondComponent != rightPanel) {
          splitter!!.secondComponent = rightPanel
        }

        if (installActions.isNotEmpty()) {
          installButton.action = wrapAction(installActions.first(), packageDetails)
          if (installActions.size > 1) {
            installButton.options = installActions
              .asSequence().drop(1).map { wrapAction(it, packageDetails) }.toList().toTypedArray()
          }
          installButton.repaint()
        } else hideInstallableControls()

        documentationUrl = packageDetails.documentationUrl
        documentationLink.isVisible = documentationUrl != null

        val renderedDescription = runCatching {
          with(packageDetails) {
            when {
              !description.isNullOrEmpty() -> service.convertToHTML(descriptionContentType, description!!)
              !summary.isNullOrEmpty() -> service.wrapHtml(summary!!)
              else -> NO_DESCRIPTION
            }
          }
        }.getOrElse {
          thisLogger().info(it)
          message("conda.packaging.error.rendering.description")
        }

        descriptionPanel.setHtml(renderedDescription)
      }
    }
  }

  private fun wrapAction(installAction: PythonPackageInstallAction, details: PythonPackageDetails): Action {
    return object : AbstractAction(installAction.text) {
      override fun actionPerformed(e: ActionEvent?) {
        val version = if (versionSelector.text == latestText) null else versionSelector.text
        packagingScope.launch {
          val specification = details.toPackageSpecification(version)
          installAction.installPackage(specification)
        }
      }
    }
  }

  fun showSearchResult(installed: List<InstalledPackage>, repoData: List<PyPackagesViewData>) {
    tablesView.showSearchResult(installed, repoData)
  }

  fun resetSearch(installed: List<InstalledPackage>, repos: List<PyPackagesViewData>) {
    tablesView.resetSearch(installed, repos)
  }

  fun startProgress() {
    progressBar.isVisible = true
    hideInstallableControls()
    hideInstalledControls()
  }

  fun stopProgress() {
    progressBar.isVisible = false
  }

  private fun showInstalledControls(managementSupported: Boolean) {
    hideInstallableControls()
    progressBar.isVisible = false
    versionLabel.isVisible = true
    uninstallAction.isVisible = managementSupported
  }

  private fun showInstallableControls(managementSupported: Boolean) {
    hideInstalledControls()
    progressBar.isVisible = false
    versionSelector.isVisible = managementSupported
    versionSelector.text = latestText
    installButton.isVisible = managementSupported
  }

  private fun hideInstalledControls() {
    versionLabel.isVisible = false
    uninstallAction.isVisible = false
  }

  private fun hideInstallableControls() {
    installButton.isVisible = false
    versionSelector.isVisible = false
  }

  private fun showHeaderForPackage(selectedPackage: DisplayablePackage, managementSupported: Boolean) {
    packageNameLabel.text = selectedPackage.name
    packageNameLabel.isVisible = true
    documentationLink.isVisible = false

    if (selectedPackage is InstalledPackage) {
      @Suppress("HardCodedStringLiteral")
      versionLabel.text = selectedPackage.instance.version
      showInstalledControls(managementSupported)
    }
    else {
      showInstallableControls(managementSupported)
    }
  }

  fun setEmpty() {
    hideInstalledControls()
    hideInstallableControls()
    listOf(packageNameLabel, packageNameLabel, documentationLink).forEach { it.isVisible = false }

    splitter?.secondComponent = noPackagePanel
  }

  override fun dispose() {
    packagingScope.cancel()
  }

  internal suspend fun recreateModulePanel() {
    val newPanel = createLeftPanel(project.service<PyPackagingToolWindowService>())
    withContext(Dispatchers.Main) {
      leftPanel = newPanel
      splitter?.firstComponent = leftPanel
      splitter?.repaint()
    }
  }

  companion object {
    private const val HORIZONTAL_SPLITTER_KEY = "Python.PackagingToolWindow.Horizontal"
    private const val VERTICAL_SPLITTER_KEY = "Python.PackagingToolWindow.Vertical"

    val NO_DESCRIPTION: String
      get() = message("python.toolwindow.packages.no.description.placeholder")
  }
}