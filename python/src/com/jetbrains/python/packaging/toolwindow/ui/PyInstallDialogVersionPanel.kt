// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.toolwindow.PyPackageIcons
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.toolwindow.details.PyPackageDetailsHtmlRender
import com.jetbrains.python.packaging.toolwindow.details.PyPackagingJcefHtmlPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.JToggleButton

internal class PyInstallDialogVersionPanel(
  private val project: Project,
  private val packagingService: PyPackagingToolWindowService,
  private val onInstall: () -> Unit,
  private val onDescriptionToggled: (visible: Boolean) -> Unit = {},
) : Disposable {
  private val LATEST_VERSION = message("python.packaging.install.dialog.version.latest")

  var selectedPackageName: String? = null
    private set
  var selectedRepository: PyPackageRepository? = null
    private set
  @NlsSafe
  var selectedVersion: String = LATEST_VERSION
    private set

  private var loadVersionsJob: Job? = null
  private var availableVersions: List<String> = emptyList()
  @Nls private var packageDescription: String? = null
  @Nls private var packageSummary: String? = null
  private var packageDescriptionContentType: String? = null
  private val presenter = PyInstallDialogVersionPresenter()
  private var loadedDetailsKey: String? = null
  private val detailsLoadMutex = kotlinx.coroutines.sync.Mutex()
  private val htmlPanel: PyPackagingJcefHtmlPanel = PyPackagingJcefHtmlPanel(project)

  val packageInfoLabel: JBLabel = JBLabel().apply { icon = PyPackageIcons.PackageGray; isVisible = false }
  val versionButton: JPanel = buildVersionSelectorPanel().apply { isVisible = false }
  val editableCheckbox: JBCheckBox = JBCheckBox(message("python.packaging.install.dialog.editable")).apply {
    isOpaque = false; isVisible = false
  }
  val installButton: JButton = JButton(message("python.packaging.install.dialog.install")).apply {
    isEnabled = false
    isVisible = false
    isOpaque = false
    putClientProperty("JButton.buttonType", "default")
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    addActionListener { onInstall() }
  }
  val descriptionToggle: JToggleButton = object : JToggleButton(AllIcons.Actions.PreviewDetailsVertically) {
    init {
      toolTipText = message("python.packaging.install.dialog.show.description")
      isBorderPainted = false
      isFocusPainted = false
      isContentAreaFilled = false
      isOpaque = false
      isFocusable = false
      isRolloverEnabled = true
      preferredSize = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      addActionListener { toggleDescription() }
    }
    override fun paintComponent(g: Graphics) {
      if (isSelected || model.isRollover) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = if (isSelected) JBUI.CurrentTheme.ActionButton.pressedBackground()
                   else JBUI.CurrentTheme.ActionButton.hoverBackground()
        g2.fillRoundRect(0, 0, width, height, 4, 4)
        g2.dispose()
      }
      super.paintComponent(g)
    }
  }
  private val descriptionPanel: JPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
    preferredSize = Dimension(-1, JBUI.scale(300))
    add(htmlPanel.component, BorderLayout.CENTER)
    isVisible = false
  }

  fun createBottomPanel(): JComponent {
    val panel = JPanel(BorderLayout()).apply {
      isOpaque = false
      preferredSize = Dimension(-1, JBUI.CurrentTheme.List.rowHeight() * 2 + JBValue.UIInteger("Component.focusWidth", 2).get() * 2)
    }
    val mainPanel = JPanel(BorderLayout()).apply {
      isOpaque = false
      border = JBUI.Borders.empty(UIUtil.getRegularPanelInsets())
    }
    mainPanel.add(packageInfoLabel, BorderLayout.WEST)

    val controlsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, UIUtil.getRegularPanelInsets().left, 0)).apply { isOpaque = false }
    controlsPanel.add(versionButton)
    controlsPanel.add(editableCheckbox)
    controlsPanel.add(installButton)

    mainPanel.add(controlsPanel, BorderLayout.EAST)
    panel.add(mainPanel, BorderLayout.CENTER)
    return panel
  }

  fun createDescriptionPanel(): JPanel = descriptionPanel

  override fun dispose() {
    Disposer.dispose(htmlPanel)
  }

  private fun applyViewState(state: PyInstallDialogVersionViewState) {
    packageInfoLabel.isVisible = state.packageInfoVisible
    installButton.isEnabled = state.installEnabled
    installButton.isVisible = state.controlsVisible
    versionButton.isVisible = state.controlsVisible
    descriptionPanel.isVisible = state.descriptionVisible
    descriptionPanel.parent?.revalidate()
    descriptionPanel.parent?.repaint()
    when (val event = state.descriptionToggleEvent) {
      is DescriptionToggleEvent.Fire -> onDescriptionToggled(event.descriptionVisible)
      is DescriptionToggleEvent.None -> Unit
    }
  }

  fun selectPackage(@NlsContexts.Label name: String, @NlsContexts.Label repoName: String) {
    selectedPackageName = name
    packageInfoLabel.text = "$name ($repoName)"
    packageInfoLabel.icon = PyPackageIcons.PackageGray
    resetVersionState(repoName)
    val state = presenter.onPackageSelected(descriptionToggle.isSelected)
    applyViewState(state)
  }

  private fun resetVersionState(@NlsContexts.Label repoName: String) {
    loadVersionsJob?.cancel()
    loadVersionsJob = null
    loadedDetailsKey = null
    availableVersions = emptyList()
    selectedVersion = LATEST_VERSION
    packageDescription = null
    packageSummary = null
    packageDescriptionContentType = null
    val sdk = packagingService.currentSdk
    selectedRepository = sdk?.let {
      PythonPackageManager.forSdk(project, it).repositoryManager.repositories.find { r -> r.name == repoName }
    }
    if (presenter.isDescriptionVisible) {
      htmlPanel.setHtml("")
      packagingService.serviceScope.launch(Dispatchers.Default) {
        ensurePackageDetailsLoaded()
        renderDescription()
      }
    }
    rebuildVersionButton()
  }

  fun clearSelection() {
    selectedPackageName = null
    selectedRepository = null
    applyViewState(presenter.onSelectionCleared())
  }

  suspend fun notifyInstallError(msg: @NlsContexts.NotificationContent String) {
    withContext(Dispatchers.EDT) {
      NotificationGroupManager.getInstance()
        .getNotificationGroup("PythonPackages")
        .createNotification(msg, NotificationType.ERROR)
        .notify(project)
    }
  }

  private suspend fun ensurePackageDetailsLoaded(): Boolean {
    val packageName = selectedPackageName ?: return false
    val repository = selectedRepository ?: return false
    val sdk = packagingService.currentSdk ?: return false
    val key = "${repository.name}|$packageName"
    if (loadedDetailsKey == key) return true
    return detailsLoadMutex.withLock {
      if (loadedDetailsKey == key) return@withLock true
      val repoManager = PythonPackageManager.forSdk(project, sdk).repositoryManager
      when (val result = repoManager.getPackageDetails(packageName, repository)) {
        is Result.Success -> {
          val details = result.result
          withContext(Dispatchers.EDT) {
            if (selectedPackageName != packageName) return@withContext
            availableVersions = if (details.availableVersions.isNotEmpty())
              listOf(LATEST_VERSION) + details.availableVersions.drop(1)
            else listOf(LATEST_VERSION)
            packageDescription = details.description
            packageSummary = details.summary
            packageDescriptionContentType = details.descriptionContentType
            rebuildVersionButton()
          }
          loadedDetailsKey = key
          true
        }
        is Result.Failure -> {
          collapseToLatestVersion()
          false
        }
      }
    }
  }

  private suspend fun collapseToLatestVersion() {
    withContext(Dispatchers.EDT) {
      availableVersions = listOf(LATEST_VERSION)
      selectedVersion = LATEST_VERSION
      packageDescription = null
      packageSummary = null
      packageDescriptionContentType = null
      rebuildVersionButton()
    }
  }

  private fun buildVersionSelectorPanel(): JPanel {
    return JPanel(BorderLayout()).apply {
      isOpaque = false
      border = JBUI.Borders.empty(JBUI.CurrentTheme.ActionsList.cellPadding().top)
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) = showVersionPopup(this@apply)
      })
      populateVersionButton(this, LATEST_VERSION)
    }
  }

  private fun rebuildVersionButton() {
    versionButton.removeAll()
    populateVersionButton(versionButton, selectedVersion)
    versionButton.revalidate(); versionButton.repaint()
  }

  private fun populateVersionButton(panel: JPanel, version: String) {
    val vPad = JBUI.CurrentTheme.ActionsList.cellPadding().top
    val hGap = JBUI.CurrentTheme.ActionsList.elementIconGap()
    panel.add(JBLabel(message("python.packaging.install.dialog.version", version), SwingConstants.LEFT).apply {
      border = JBUI.Borders.empty(vPad, hGap, vPad, hGap)
      isOpaque = false
      foreground = UIUtil.getLabelForeground()
    }, BorderLayout.CENTER)
    panel.add(JBLabel(AllIcons.General.ChevronDown).apply {
      border = JBUI.Borders.empty(vPad, 0, vPad, hGap)
    }, BorderLayout.EAST)
  }

  private fun showVersionPopup(component: JComponent) {
    if (selectedPackageName == null) return
    loadVersionsJob?.cancel()
    loadVersionsJob = packagingService.serviceScope.launch {
      val ok = ensurePackageDetailsLoaded()
      if (!ok || availableVersions.isEmpty()) return@launch
      withContext(Dispatchers.EDT) {
        if (!component.isShowing) return@withContext
        val popup = buildVersionChooserPopup(items = availableVersions) { chosen ->
          selectedVersion = chosen
          rebuildVersionButton()
        }
        popup.showUnderneathOf(component)
      }
    }
  }

  private fun toggleDescription() {
    val state = presenter.onDescriptionToggled()
    applyViewState(state)
    if (state.descriptionVisible) {
      packagingService.serviceScope.launch(Dispatchers.Default) {
        ensurePackageDetailsLoaded()
        renderDescription()
      }
    }
  }

  private suspend fun renderDescription() {
    val render = PyPackageDetailsHtmlRender(project, packagingService.currentSdk)
    htmlPanel.setHtml(buildDescriptionHtml(render))
  }

  private suspend fun buildDescriptionHtml(render: PyPackageDetailsHtmlRender): String {
    val description = packageDescription
    val summary = packageSummary
    if (!description.isNullOrEmpty()) {
      if (decideDescriptionRender(packageDescriptionContentType) == DescriptionRenderMode.RICH) {
        return render.getHtml(object : PythonPackageDetails {
          override val name = selectedPackageName ?: ""
          override val availableVersions = this@PyInstallDialogVersionPanel.availableVersions
          override val repository = selectedRepository ?: PyPiPackageRepository
          override val summary = this@PyInstallDialogVersionPanel.packageSummary
          override val description = this@PyInstallDialogVersionPanel.packageDescription
          override val descriptionContentType = this@PyInstallDialogVersionPanel.packageDescriptionContentType
          override val documentationUrl: String? = null
        })
      }
      return "<html><body><p>${StringUtil.escapeXmlEntities(description)}</p></body></html>"
    }
    if (!summary.isNullOrEmpty()) return "<html><body><p>${StringUtil.escapeXmlEntities(summary)}</p></body></html>"
    return "<html><body><p>${message("python.toolwindow.packages.no.description.placeholder")}</p></body></html>"
  }
}

/**
 * How the install dialog should turn a package's `description` field into HTML for the JCEF panel.
 *
 * The decision follows PEP 566 / Core Metadata 2.1: a package can declare `Description-Content-Type`
 * with a media type (`text/plain`, `text/x-rst`, `text/markdown`) and an optional charset / variant
 * suffix (`; charset=UTF-8`, `; variant=GFM`). We render `text/markdown` and `text/x-rst` through
 * the full [com.jetbrains.python.packaging.toolwindow.details.PyPackageDetailsHtmlRender]
 * (markdown → HTML, RST → HTML), and treat a missing/empty content type as [RICH] too because
 * PEP 566 allows omitting the field and most popular packages still ship RST in the description.
 *
 * Anything else (notably `text/plain` and unrecognised media types) falls into [PLAIN], which the
 * caller escapes via [com.intellij.openapi.util.text.StringUtil.escapeXmlEntities] before wrapping
 * in a `<pre>`/`<p>` block.
 */
internal enum class DescriptionRenderMode { RICH, PLAIN }

/** @see DescriptionRenderMode */
internal fun decideDescriptionRender(contentType: String?): DescriptionRenderMode {
  val mediaType = contentType?.substringBefore(';')?.trim().orEmpty()
  return when (mediaType.lowercase()) {
    "text/markdown", "text/x-rst", "" -> DescriptionRenderMode.RICH
    else -> DescriptionRenderMode.PLAIN
  }
}
