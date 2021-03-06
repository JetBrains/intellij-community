// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.util.Alarm
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle.message
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent

class PyPackagingToolWindowPanel(service: PyPackagingToolWindowService, toolWindow: ToolWindow) : SimpleToolWindowPanel(false, true) {
  private val installedPackagesHeader = JLabel(message("python.toolwindow.packages.installed.label")).apply { icon = AllIcons.General.ArrowDown }
  private val pypiHeaderLabel = JLabel(message("python.toolwindow.packages.pypi.repo.label")).apply { icon = AllIcons.General.ArrowDown }
  private val packageNameLabel = JLabel().apply { font = JBFont.h4().asBold(); isVisible = false }
  private val versionLabel = JLabel().apply { isVisible = false }
  private val documentationLink = HyperlinkLabel(message("python.toolwindow.packages.documentation.link")).apply {
    addHyperlinkListener { if (documentationUrl != null) BrowserUtil.browse(documentationUrl!!) }
    isVisible = false
  }

  private val searchTextField: SearchTextField
  private val searchAlarm: Alarm
  private val pypiHeaderPanel: JPanel
  private val installedPackagesTable: PyPackagesTable<InstalledPackage>
  private val availablePackages: PyPackagesTable<DisplayablePackage>
  private val installButton: JBOptionButton
  private val uninstallAction: JComponent
  private val progressBar: JProgressBar
  private val versionSelector: JBComboBoxLabel
  private val descriptionPanel: JCEFHtmlPanel
  private var currentPackageInfo: PackageInfo? = null
  private var documentationUrl: String? = null

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


  init {
    withEmptyText(message("python.toolwindow.packages.no.interpreter.text"))
    installedPackagesTable = PyPackagesTable(PyPackagesTableModel(), service)
    availablePackages = PyPackagesTable(PyPackagesTableModel(), service)
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
              override fun onChosen(@NlsSafe selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
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


    uninstallAction = ActionManager.getInstance()
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
      .component

    progressBar = JProgressBar(JProgressBar.HORIZONTAL).apply {
      maximumSize.width = 200
      minimumSize.width = 200
      preferredSize.width = 200
      isVisible = false
      isIndeterminate = true
    }

    pypiHeaderPanel = headerPanel(pypiHeaderLabel, availablePackages)
    leftPanel = ScrollPaneFactory.createScrollPane(JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      alignmentX = LEFT_ALIGNMENT
      background = UIUtil.getListBackground()
      add(headerPanel(installedPackagesHeader, installedPackagesTable))
      add(installedPackagesTable)
      add(pypiHeaderPanel)
      availablePackages.isVisible = false
      add(availablePackages)
    }, true)

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
        preferredSize = Dimension(250, 25)
        minimumSize = Dimension(250, 25)
        maximumSize = Dimension(250, 25)
        textEditor.border = JBUI.Borders.empty(0, 6, 0, 0)
        textEditor.isOpaque = true
        textEditor.emptyText.text = message("python.toolwindow.packages.search.text.placeholder")
      }

      override fun onFieldCleared() {
        service.handleSearch("")
      }
    }

    searchAlarm = SingleAlarm(Runnable {
      installedPackagesTable.selectionModel.clearSelection()
      service.handleSearch(searchTextField.text.trim())
    }, 500, ModalityState.NON_MODAL, service)

    searchTextField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        searchAlarm.cancelAndRequest()
      }
    })


    initOrientation(true)
    trackOrientation(service.project, service)
  }

  private fun initOrientation(horizontal: Boolean) {
    val jbPanelWithEmptyText = JBPanelWithEmptyText()
    jbPanelWithEmptyText.emptyText.text = message("python.toolwindow.packages.description.panel.placeholder")

    val second = if (splitter?.secondComponent == rightPanel) rightPanel else jbPanelWithEmptyText
    val proportionKey = if (horizontal) HORIZONTAL_SPLITTER_KEY else VERTICAL_SPLITTER_KEY
    splitter = OnePixelSplitter(!horizontal, proportionKey, 0.3f).apply {
      firstComponent = leftPanel
      secondComponent = second
    }

    mainPanel = borderPanel {
      val topToolbar = boxPanel {
        border = SideBorder(UIUtil.getBoundsColor(), SideBorder.BOTTOM)
        preferredSize = Dimension(preferredSize.width, 25)
        minimumSize = Dimension(minimumSize.width, 25)
        maximumSize = Dimension(maximumSize.width, 25)
        add(searchTextField)
      }
      add(topToolbar, BorderLayout.NORTH)
      add(splitter!!, BorderLayout.CENTER)
    }
    setContent(mainPanel!!)
  }

  private fun trackOrientation(project: Project, service: PyPackagingToolWindowService) {
    project.messageBus.connect(service).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      var myHorizontal = true
      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val toolWindow = toolWindowManager.getToolWindow("Python Packages")
        if (toolWindow == null || toolWindow.isDisposed) return
        val isHorizontal = toolWindow.anchor.isHorizontal

        if (myHorizontal != isHorizontal) {
          myHorizontal = isHorizontal
          val content = toolWindow.contentManager.contents.find { it?.component is PyPackagingToolWindowPanel }
          val panel = content?.component as? PyPackagingToolWindowPanel ?: return
          panel.initOrientation(myHorizontal)
        }
      }
    })
  }


  fun showSearchResult(installed: List<InstalledPackage>, available: List<DisplayablePackage>, exactMatch: Int) {
    with(availablePackages) {
      items = available
      revalidate()
      repaint()
    }
    with(installedPackagesTable) {
      items = installed
      revalidate()
      repaint()
    }

    availablePackages.isVisible = true
    pypiHeaderPanel.isVisible = true
    pypiHeaderLabel.icon = AllIcons.General.ArrowDown
    updatePackageHeaders()
    if (exactMatch != -1) ApplicationManager.getApplication().invokeLater { scrollToPackage(exactMatch) }
  }

  fun resetSearch(installed: List<InstalledPackage>) {
    availablePackages.isVisible = false
    pypiHeaderPanel.isVisible = false
    with(installedPackagesTable) {
      items = installed
      revalidate()
      repaint()
    }
    updatePackageHeaders()
  }

  fun displaySelectedPackage(selectedPackage: DisplayablePackage, packageInfo: PackageInfo) {
    packageNameLabel.text = selectedPackage.name
    packageNameLabel.isVisible = true
    currentPackageInfo = packageInfo
    if (splitter?.secondComponent != rightPanel) {
      splitter!!.secondComponent = rightPanel
    }

    descriptionPanel.setHtml(packageInfo.description)
    documentationUrl = packageInfo.documentationUrl
    documentationLink.isVisible = documentationUrl != null

    if (selectedPackage is InstalledPackage) {
      versionLabel.text = selectedPackage.instance.version
      showInstalledControls()
    }
    else {
      showInstallableControls()
    }
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

  fun packageInstalled(selectedPackage: InstalledPackage, newFiltered: List<InstalledPackage>) {
    updateInstalledPackagesTable(newFiltered)
    var selectedIndex: Int? = null
    newFiltered.forEach { pkg ->
      val existingIndex = availablePackages.items.indexOfFirst { pkg.name == it.name }
      if (selectedIndex == null && pkg.name == selectedPackage.name) selectedIndex = existingIndex
      if (existingIndex != -1) {
        availablePackages.listModel.removeRow(existingIndex)
        availablePackages.listModel.insertRow(existingIndex, pkg)
      }
    }
    selectedIndex?.let {
      availablePackages.clearSelection()
      availablePackages.selectionModel.setSelectionInterval(it, it)
    }
    updatePackageHeaders()
  }

  fun packageDeleted(deletedPackage: InstallablePackage) {
    val indexInstalled = installedPackagesTable.items.indexOfFirst { it.name == deletedPackage.name }
    if (indexInstalled != -1) {
      if (installedPackagesTable.selectedRow == indexInstalled) installedPackagesTable.selectionModel.clearSelection()
      installedPackagesTable.listModel.removeRow(indexInstalled)
    }
    val indexAvailable = availablePackages.items.indexOfFirst { it.name == deletedPackage.name }
    if (indexAvailable != -1) {
      if (availablePackages.selectedRow == indexAvailable) installedPackagesTable.selectionModel.clearSelection()
      availablePackages.listModel.removeRow(indexAvailable)
      availablePackages.listModel.insertRow(indexAvailable, deletedPackage)
    }
    updatePackageHeaders()
    showInstallableControls()
  }

  fun updateInstalledPackagesTable(newFiltered: List<InstalledPackage>) {
    installedPackagesTable.listModel.addRows(newFiltered)
  }

  private fun scrollToPackage(exactMatch: Int) {
    availablePackages.setRowSelectionInterval(exactMatch, exactMatch)
    availablePackages.columnModel.selectionModel.setSelectionInterval(0, 0)
    (availablePackages.selectionModel as DefaultListSelectionModel).setSelectionInterval(exactMatch, exactMatch)
    val viewport = availablePackages.parent.parent as JBViewport
    val rectangle = availablePackages.getCellRect(exactMatch, 0, true)
    val visibleRectangle = viewport.visibleRect
    viewport.scrollRectToVisible(Rectangle(rectangle.x, rectangle.y, visibleRectangle.width, visibleRectangle.height))
  }

  private fun updatePackageHeaders() {
    if (availablePackages.isVisible) {
      installedPackagesHeader.text = message("python.toolwindow.packages.installed.label.searched", installedPackagesTable.items.size)
      pypiHeaderLabel.text = message("python.toolwindow.packages.pypi.repo.label.searched", availablePackages.items.size)
    }
    else {
      installedPackagesHeader.text = message("python.toolwindow.packages.installed.label")
      pypiHeaderLabel.text = message("python.toolwindow.packages.pypi.repo.label")
    }
  }


  companion object {
    private const val HORIZONTAL_SPLITTER_KEY = "Python.PackagingToolWindow.Horizontal"
    private const val VERTICAL_SPLITTER_KEY = "Python.PackagingToolWindow.Vertical"

    val REQUEST_FAILED_TEXT: String
      get() = message("python.toolwindow.packages.request.failed")
    val NO_DESCRIPTION: String
      get() = message("python.toolwindow.packages.no.description.placeholder")
  }
}