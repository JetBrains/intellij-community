// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
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
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.util.Alarm
import com.intellij.util.Alarm.ThreadToUse
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle.message
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent

class PyPackagingToolWindowPanel(service: PyPackagingToolWindowService, toolWindow: ToolWindow) : SimpleToolWindowPanel(false, true) {
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
  private var currentPackageInfo: PackageInfo? = null
  private var documentationUrl: String? = null

  val packageListPanel: JPanel
  val tablesView: PyPackagingTablesView
  val noPackagePanel = JBPanelWithEmptyText().apply { emptyText.text = message("python.toolwindow.packages.description.panel.placeholder") }

  // layout
  private var mainPanel: JPanel? = null
  private var splitter: OnePixelSplitter? = null
  private val leftPanel: JScrollPane
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
    withEmptyText(message("python.toolwindow.packages.no.interpreter.text"))
    descriptionPanel = PyPackagingJcefHtmlPanel(service.project)
    Disposer.register(toolWindow.disposable, descriptionPanel)

    versionSelector = JBComboBoxLabel().apply {
      text = latestText
      isVisible = false
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
          val versions = listOf(latestText) + (currentPackageInfo?.availableVersions ?: emptyList())
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

    // todo: add options to install with args
    installButton = JBOptionButton(object : AbstractAction(message("python.toolwindow.packages.install.button")) {
      override fun actionPerformed(e: ActionEvent?) {
        val version = if (versionSelector.text == latestText) null else versionSelector.text
        service.installSelectedPackage(version)
      }
    }, null).apply { isVisible = false }


    val uninstallToolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, DefaultActionGroup(DefaultActionGroup().apply {
        add(object : AnAction(message("python.toolwindow.packages.delete.package")) {
          override fun actionPerformed(e: AnActionEvent) {
            service.deleteSelectedPackage()
          }
        })
        isPopup = true
        with(templatePresentation) {
          putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
          icon = AllIcons.Actions.More
        }
      }), true)
    uninstallToolbar.setTargetComponent(this)
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

    tablesView = PyPackagingTablesView(service, packageListPanel)
    leftPanel = ScrollPaneFactory.createScrollPane(packageListPanel, true)

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
    }, 500, service, ThreadToUse.SWING_THREAD, ModalityState.NON_MODAL)

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
    actionToolbar.setTargetComponent(this)


    val installFromLocationLink = DropDownLink(message("python.toolwindow.packages.add.package.action"),
                                               listOf(fromVcsText, fromDiscText)) {
      val params = when (it) {
        fromDiscText -> showInstallFromDiscDialog(service)
        fromVcsText -> showInstallFromVcsDialog(service)
        else -> throw IllegalStateException("Unknown operation")
      }
      if (params != null) {
        service.installFromLocation(params.first, params.second)
      }
    }

    mainPanel = borderPanel {
      val topToolbar = boxPanel {
        border = SideBorder(NamedColorUtil.getBoundsColor(), SideBorder.BOTTOM)
        preferredSize = Dimension(preferredSize.width, 30)
        minimumSize = Dimension(minimumSize.width, 30)
        maximumSize = Dimension(maximumSize.width, 30)
        add(searchTextField)
        actionToolbar.component.maximumSize = Dimension(60, actionToolbar.component.maximumSize.height)
        add(actionToolbar.component)
        add(installFromLocationLink)
      }
      add(topToolbar, BorderLayout.NORTH)
      add(splitter!!, BorderLayout.CENTER)
    }
    setContent(mainPanel!!)
  }

  private fun showInstallFromVcsDialog(service: PyPackagingToolWindowService): Pair<String, Boolean>? {
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
          .horizontalAlign(HorizontalAlign.FILL)
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
      return Pair(prefix + link, editable)
    }
    return null
  }

  private fun showInstallFromDiscDialog(service: PyPackagingToolWindowService): Pair<String, Boolean>? {
    var editable = false

    val textField = TextFieldWithBrowseButton().apply {
      addBrowseFolderListener(message("python.toolwindow.packages.add.package.path.selector"), "", service.project,
                              FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor())
    }
    val panel = panel {
      row(message("python.toolwindow.packages.add.package.path")) {
        cell(textField)
          .columns(COLUMNS_MEDIUM)
          .horizontalAlign(HorizontalAlign.FILL)
      }
      row {
        checkBox(message("python.toolwindow.packages.add.package.as.editable"))
          .bindSelected({ editable }, { editable = it })
      }
    }

    val shouldInstall = dialog(message("python.toolwindow.packages.add.package.dialog.title"), panel, project = service.project, resizable = true).showAndGet()
    return if (shouldInstall) Pair("file://${textField.text}", editable) else null
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

  fun showSearchResult(installed: List<InstalledPackage>, repoData: List<PyPackagesViewData>) {
    tablesView.showSearchResult(installed, repoData)
  }

  fun resetSearch(installed: List<InstalledPackage>, repos: List<PyPackagesViewData>) {
    tablesView.resetSearch(installed, repos)
  }

  fun displaySelectedPackageInfo(packageInfo: PackageInfo) {
    currentPackageInfo = packageInfo
    if (splitter?.secondComponent != rightPanel) {
      splitter!!.secondComponent = rightPanel
    }

    descriptionPanel.setHtml(packageInfo.description)
    documentationUrl = packageInfo.documentationUrl
    documentationLink.isVisible = documentationUrl != null
  }


  fun startProgress() {
    progressBar.isVisible = true
    hideInstallableControls()
    hideInstalledControls()
  }

  fun stopProgress() {
    progressBar.isVisible = false
  }

  fun showInstalledControls() {
    hideInstallableControls()
    progressBar.isVisible = false
    versionLabel.isVisible = true
    uninstallAction.isVisible = true
  }

  fun showInstallableControls() {
    hideInstalledControls()
    progressBar.isVisible = false
    versionSelector.isVisible = true
    versionSelector.text = latestText
    installButton.isVisible = true
  }

  private fun hideInstalledControls() {
    versionLabel.isVisible = false
    uninstallAction.isVisible = false
  }

  private fun hideInstallableControls() {
    installButton.isVisible = false
    versionSelector.isVisible = false
  }

  fun packageInstalled(newFiltered: List<InstalledPackage>) {
    tablesView.packagesAdded(newFiltered)
  }

  fun packageDeleted(deletedPackage: DisplayablePackage) {
    tablesView.packageDeleted(deletedPackage)
  }

  fun showHeaderForPackage(selectedPackage: DisplayablePackage) {
    packageNameLabel.text = selectedPackage.name
    packageNameLabel.isVisible = true
    documentationLink.isVisible = false

    if (selectedPackage is InstalledPackage) {
      versionLabel.text = selectedPackage.instance.version
      showInstalledControls()
    }
    else {
      showInstallableControls()
    }
  }

  fun setEmpty() {
    hideInstalledControls()
    hideInstallableControls()
    listOf(packageNameLabel, packageNameLabel, documentationLink).forEach { it.isVisible = false }

    splitter?.secondComponent = noPackagePanel
  }


  companion object {
    private const val HORIZONTAL_SPLITTER_KEY = "Python.PackagingToolWindow.Horizontal"
    private const val VERTICAL_SPLITTER_KEY = "Python.PackagingToolWindow.Vertical"


    val REMOTE_INTERPRETER_TEXT: String
      get() = message("python.toolwindow.packages.remote.interpreter.placeholder")
    val REQUEST_FAILED_TEXT: String
      get() = message("python.toolwindow.packages.request.failed")
    val NO_DESCRIPTION: String
      get() = message("python.toolwindow.packages.no.description.placeholder")
  }
}