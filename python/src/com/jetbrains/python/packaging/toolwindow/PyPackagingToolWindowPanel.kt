// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
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
import com.intellij.util.childScope
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.common.PythonLocalPackageSpecification
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonVcsPackageSpecification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent

class PyPackagingToolWindowPanel(private val project: Project, toolWindow: ToolWindow) : SimpleToolWindowPanel(false, true), Disposable  {
  private val packagingScope = ApplicationManager.getApplication().coroutineScope.childScope(Dispatchers.Default)
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
    showHeaderForPackage(selectedPackage)
    this.selectedPackage = selectedPackage
    packagingScope.launch(Dispatchers.IO) {
      val service = project.service<PyPackagingToolWindowService>()
      val packageDetails = service.detailsForPackage(selectedPackage)

      val installActions = PythonPackagingToolwindowActionProvider.EP_NAME
        .extensionList
        .firstNotNullOf {
          it.getInstallActions(packageDetails, service.manager)
        }

      withContext(Dispatchers.Main) {
        selectedPackageDetails = packageDetails

        if (splitter?.secondComponent != rightPanel) {
          splitter!!.secondComponent = rightPanel
        }

        val renderedDescription  = with(packageDetails) {
          when {
            !description.isNullOrEmpty() -> service.convertToHTML(descriptionContentType, description!!)
            !summary.isNullOrEmpty() -> service.wrapHtml(summary!!)
            else -> NO_DESCRIPTION
          }
        }

        descriptionPanel.setHtml(renderedDescription)
        documentationUrl = packageDetails.documentationUrl
        documentationLink.isVisible = documentationUrl != null


        installButton.action = wrapAction(installActions.first(), packageDetails)
        if (installActions.size > 1) {
          installButton.options = installActions
            .asSequence().drop(1).map { wrapAction(it, packageDetails) }.toList().toTypedArray()
        }

        installButton.repaint()
      }
    }
  }

  private fun wrapAction(installAction: PythonPackageInstallAction, details: PythonPackageDetails): Action {
    return object : AbstractAction(installAction.text) {
      override fun actionPerformed(e: ActionEvent?) {
        val version = if (versionSelector.text == latestText) null else versionSelector.text
        packagingScope.launch(Dispatchers.IO) {
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

  private fun showInstalledControls() {
    hideInstallableControls()
    progressBar.isVisible = false
    versionLabel.isVisible = true
    uninstallAction.isVisible = true
  }

  private fun showInstallableControls() {
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

  private fun showHeaderForPackage(selectedPackage: DisplayablePackage) {
    packageNameLabel.text = selectedPackage.name
    packageNameLabel.isVisible = true
    documentationLink.isVisible = false

    if (selectedPackage is InstalledPackage) {
      @Suppress("HardCodedStringLiteral")
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

  override fun dispose() {
    packagingScope.cancel()
  }

  companion object {
    private const val HORIZONTAL_SPLITTER_KEY = "Python.PackagingToolWindow.Horizontal"
    private const val VERTICAL_SPLITTER_KEY = "Python.PackagingToolWindow.Vertical"

    val NO_DESCRIPTION: String
      get() = message("python.toolwindow.packages.no.description.placeholder")
  }
}