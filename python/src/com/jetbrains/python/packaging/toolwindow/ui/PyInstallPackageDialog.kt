// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.BigPopupUI
import com.intellij.python.pytools.PyTool
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.components.SearchFieldWithExtension
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.wm.WindowManager
import com.intellij.python.processOutput.common.ProcessOutputQuery
import com.intellij.python.processOutput.common.sendProcessOutputQuery
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.sdk.findFirstPythonSdk
import com.jetbrains.python.sdk.findModuleForSdk
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyPackageVersionNormalizer
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.SwingUtilities

internal class PyInstallPackageDialog(private val project: Project) : BigPopupUI(project) {

  companion object {
    private const val LOCATION_SETTINGS_KEY: String = "py.install.package.dialog.popup"
    private const val FULL_SIZE_KEY: String = "py.install.package.dialog.popup.full"
  }

  private val packagingService = project.service<PyPackagingToolWindowService>()
  private val workspaceSelector = PyInstallDialogWorkspaceSelector(project, packagingService)
  private val versionPanel = PyInstallDialogVersionPanel(
    project, packagingService,
    onInstall = { performInstall() },
    onDescriptionToggled = { visible ->
      swapListAndDescription(visible)
      mySearchField.requestFocusInWindow()
    },
  )
  private val resultsList = PyInstallDialogResultsList(
    project, packagingService,
    onPackageSelected = { name, repo -> versionPanel.selectPackage(name, repo) },
    onCommandSelected = { cmd -> mySearchField.text = "$cmd "; mySearchField.requestFocusInWindow() },
    onSelectionCleared = { versionPanel.clearSelection() },
    onResultsUpdated = { _ -> },
  )

  /**
   * File-chooser descriptor for the "Browse for a local distribution" action in the search field.
   *
   * Accepts the three distribution formats pip and uv natively install from disk:
   *  - `*.whl`    — built distribution (PEP 427 / wheel binary)
   *  - `*.tar.gz` — source distribution (sdist), the format `setup.py sdist` / `python -m build`
   *                 produce by default
   *  - `*.zip`    — alternative source distribution archive accepted by pip when a project sets
   *                 `sdist --formats=zip`
   *
   * Plus directories (chosen via [FileChooserDescriptorFactory.singleFileOrDir]) so the user can
   * point at an unpacked source checkout for `pip install <dir>` / editable installs.
   */
  private val packageFileDescriptor: FileChooserDescriptor =
    FileChooserDescriptorFactory.singleFileOrDir()
      .withExtensionFilter(message("python.packaging.install.dialog.browse.filter"), "whl", "tar.gz", "zip")

  private lateinit var bottomContainer: JPanel
  private lateinit var listScrollPane: JScrollPane
  private lateinit var listOrDescContainer: JPanel
  // Assigned by the popup-creation method that always runs before any code path that reads
  // `popup` (the field is only read from user-driven actions — key handlers, resize callbacks,
  // install-completion callbacks — all of which the popup itself installs after this assignment).
  // Kept as `lateinit` (rather than nullable) so downstream call sites don't have to guard.
  private lateinit var popup: JBPopup

  private var currentMode: DialogMode = DialogMode.SEARCH
  private var currentStrategy: DialogModeStrategy = DialogModeStrategy.Search
  private var directInstallText: String = ""
  private var pendingAutoSelect: String? = null
  private var balloonFullSize: Dimension? = null
  private var collapsedSize: Dimension? = null

  override fun createList(): JBList<Any> = resultsList.list

  override fun createCellRenderer(): ListCellRenderer<Any> = resultsList.renderer

  override fun createHeader(): JComponent =
    workspaceSelector.createTopPanel(versionPanel.descriptionToggle)

  override fun getAccessibleName(): @Nls String = message("python.packaging.install.dialog.accessible.name")

  override fun installScrollingActions() {
    com.intellij.ui.ScrollingUtil.installActions(resultsList.list, mySearchField, false)
  }

  override fun createListPane(): JScrollPane {
    val scroll = super.createListPane()
    val cur = scroll.preferredSize
    // BigPopupUI sets a 670px-wide list pane by default; the install dialog needs more horizontal
    // room for package name + version + module + group columns, so scale the width 1.5×.
    scroll.preferredSize = Dimension((cur.width * 1.5f).toInt(), JBUI.scale(300))
    listScrollPane = scroll
    resultsList.attachToScroll(scroll)
    val refocus = object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) { mySearchField.requestFocusInWindow() }
    }
    resultsList.list.addMouseListener(refocus)
    scroll.viewport.addMouseListener(refocus)
    return scroll
  }

  override fun wrapSearchField(): JComponent {
    if (!Registry.`is`("search.everywhere.round.text.field", false)) return mySearchField
    val wrapper = Wrapper(SearchFieldWithExtension(mySearchField, JBUI.CurrentTheme.Popup.BACKGROUND))
    wrapper.isOpaque = true
    wrapper.background = JBUI.CurrentTheme.Popup.BACKGROUND
    wrapper.border = JBUI.Borders.empty(3, 5)
    return wrapper
  }

  override fun createSuggestionsPanel(): JPanel {
    listOrDescContainer = JPanel(BorderLayout()).apply {
      isOpaque = false
      add(createListPane(), BorderLayout.CENTER)
    }
    return listOrDescContainer
  }

  private fun buildBottomContainer(): JPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
    add(versionPanel.createBottomPanel(), BorderLayout.CENTER)
    isVisible = false
  }

  override fun dispose() {
    Disposer.dispose(versionPanel)
  }

  fun show(
    initialSearchText: String? = null,
    preselectModuleName: String? = null,
    preselectGroupName: String? = null,
  ) {
    if (::popup.isInitialized) {
      if (!popup.isDisposed) popup.content.requestFocus()
      return
    }
    if (preselectModuleName != null || preselectGroupName != null) {
      workspaceSelector.preselect(preselectModuleName, preselectGroupName)
    }
    ensureSdkInitialized()
    init()
    myResultsList.putClientProperty(com.intellij.ui.render.RenderingUtil.ALWAYS_PAINT_SELECTION_AS_FOCUSED, true)
    bottomContainer = buildBottomContainer()
    addToBottom(bottomContainer)
    setupSearchListener()
    setupExtensions()
    setupEnterKeyHandler()
    updatePlaceholder()
    addListDataListener(resultsList.model)

    pendingAutoSelect = initialSearchText

    popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(this, mySearchField)
      .setProject(project)
      .setModalContext(false)
      .setCancelOnWindowDeactivation(false)
      .setCancelOnClickOutside(true)
      .setRequestFocus(true)
      .setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(project, LOCATION_SETTINGS_KEY, true)
      .setLocateWithinScreenBounds(false)
      .createPopup()

    registerKeyboardAction(
      { popup.cancel() },
      KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
      WHEN_IN_FOCUSED_WINDOW,
    )

    val balloon = popup
    Disposer.register(project, balloon)
    val initialMin = minimumSize
    JBInsets.addTo(initialMin, balloon.content.insets)
    balloon.setMinimumSize(initialMin)
    val stateService = WindowStateService.getInstance(project)
    balloonFullSize = stateService.getSize(FULL_SIZE_KEY)
    val savedSize = stateService.getSize(LOCATION_SETTINGS_KEY)

    val resizeListener = object : java.awt.event.ComponentAdapter() {
      override fun componentResized(e: java.awt.event.ComponentEvent?) {
        if (myViewType != ViewType.FULL) return
        val s = balloon.size
        JBInsets.removeFrom(s, balloon.content.insets)
        balloonFullSize = s
      }
    }
    balloon.content.addComponentListener(resizeListener)

    Disposer.register(balloon, Disposable {
      balloonFullSize?.let { stateService.putSize(FULL_SIZE_KEY, it) }
      dispose()
    })

    addViewTypeListener { type ->
      val balloon = popup ?: return@addViewTypeListener
      bottomContainer.isVisible = (type == ViewType.FULL || currentMode != DialogMode.SEARCH)
      ApplicationManager.getApplication().invokeLater {
        applyBalloonSize(balloon, type)
      }
    }

    when (myViewType) {
      ViewType.SHORT -> {
        val shortSize = Dimension(preferredSize)
        savedSize?.width?.let { shortSize.width = it }
        JBInsets.addTo(shortSize, balloon.content.insets)
        balloon.size = shortSize
        collapsedSize = Dimension(shortSize)
      }
      ViewType.FULL -> Unit
    }
    showPopup()
    SwingUtilities.invokeLater {
      SwingUtilities.getRootPane(versionPanel.installButton)?.defaultButton = versionPanel.installButton
    }
    if (initialSearchText != null) {
      mySearchField.text = initialSearchText
      mySearchField.selectAll()
    }
    else {
      mySearchField.requestFocusInWindow()
    }
  }

  private fun applyBalloonSize(balloon: JBPopup, type: ViewType) {
    if (balloon.isDisposed) return
    val current = balloon.size
    val insets = balloon.content.insets
    when (type) {
      ViewType.SHORT -> {
        val withoutInsets = Dimension(current)
        JBInsets.removeFrom(withoutInsets, insets)
        balloonFullSize = withoutInsets
        refreshPopupSize()
      }
      ViewType.FULL -> {
        val savedHeight = balloonFullSize?.height ?: preferredSize.height
        val targetHeight = savedHeight + insets.top + insets.bottom
        val newSize = Dimension(current.width, targetHeight)
        val min = balloon.content.minimumSize
        if (newSize.height < min.height) newSize.height = min.height
        balloon.size = newSize
      }
    }
  }

  private fun showPopup() {
    val window = WindowManager.getInstance().suggestParentWindow(project)
    val parent = window?.let { UIUtil.findUltimateParent(it) }
    if (parent != null) {
      val content = popup.content
      val balloonSize = content.preferredSize
      val screenPoint = Point(
        (parent.size.width - balloonSize.width) / 2,
        parent.height / 4 - balloonSize.height / 2,
      )
      SwingUtilities.convertPointToScreen(screenPoint, parent)
      val screenRectangle = ScreenUtil.getScreenRectangle(screenPoint)
      val insets = content.insets
      val bottomEdge = screenPoint.y + expandedSize.height + insets.bottom + insets.top
      val shift = bottomEdge - screenRectangle.maxY.toInt()
      if (shift > 0) screenPoint.y = maxOf(screenPoint.y - shift, screenRectangle.y)
      popup.show(RelativePoint(screenPoint))
    }
    else {
      popup.showCenteredInCurrentWindow(project)
    }
  }

  private fun setupEnterKeyHandler() {
    mySearchField.registerKeyboardAction(
      { if (versionPanel.installButton.isVisible && versionPanel.installButton.isEnabled) performInstall() },
      KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
      WHEN_FOCUSED,
    )
  }

  private fun updatePlaceholder() {
    mySearchField.emptyText.text = message("python.packaging.install.dialog.search.placeholder")
    mySearchField.putClientProperty(
      com.intellij.ui.components.TextComponentEmptyText.STATUS_VISIBLE_FUNCTION,
      java.util.function.Predicate<javax.swing.text.JTextComponent> { it.text.isNullOrEmpty() }
    )
  }

  private fun setupExtensions() {
    val leadExt = object : ExtendableTextComponent.Extension {
      override fun getIcon(hovered: Boolean): javax.swing.Icon = AllIcons.Actions.Forward
      override fun isIconBeforeText(): Boolean = true
      override fun getIconGap(): Int = JBUIScale.scale(if (ExperimentalUI.isNewUI()) 6 else 10)
    }
    mySearchField.addExtension(leadExt)

    val browseTooltip = message("python.packaging.install.dialog.browse.tooltip")
    val browseExt = ExtendableTextComponent.Extension.create(
      AllIcons.General.OpenDisk, browseTooltip, Runnable { openFileBrowser() },
    )
    mySearchField.addExtension(browseExt)
  }

  private fun swapListAndDescription(showDescription: Boolean) {
    listOrDescContainer.removeAll()
    val center: JComponent = if (showDescription) {
      val splitter = OnePixelSplitter(true, "py.install.package.dialog.splitter", 0.5f)
      splitter.divider.background = JBUI.CurrentTheme.Separator.color()
      splitter.firstComponent = listScrollPane
      splitter.secondComponent = versionPanel.createDescriptionPanel()
      splitter
    }
    else listScrollPane
    listOrDescContainer.add(center, BorderLayout.CENTER)
    listOrDescContainer.revalidate()
    listOrDescContainer.repaint()
  }

  private fun ensureSdkInitialized() {
    if (packagingService.currentSdk != null) return
    packagingService.serviceScope.launch(Dispatchers.IO) {
      val sdk = readAction { project.findFirstPythonSdk() }
                ?: return@launch
      packagingService.initForSdk(sdk)
      withContext(Dispatchers.EDT) {
        updatePlaceholder()
        val q = mySearchField.text.trim()
        if (q.isNotEmpty()) handleTextChange()
      }
    }
  }

  private fun collapseToInitialSize() {
    val target = collapsedSize ?: return
    ApplicationManager.getApplication().invokeLater {
      if (popup.isDisposed) return@invokeLater
      // setText fires document listeners as remove+insert; the transient blank state between the
      // two events schedules this collapse. If a subsequent handleTextChange has already switched
      // the dialog into a non-blank mode (DirectInstall / Command), skip collapse so we do not
      // undo its visibility changes.
      if (mySearchField.text.trim().isNotEmpty()) return@invokeLater
      bottomContainer.isVisible = false
      val current = popup.size
      popup.size = Dimension(current.width, target.height)
      popup.content.revalidate()
      popup.content.repaint()
    }
  }

  private fun refreshPopupSize() {
    ApplicationManager.getApplication().invokeLater {
      if (popup.isDisposed) return@invokeLater
      val content = popup.content
      content.preferredSize = null
      invalidateTree(content)
      val insets = content.insets
      val targetHeight = preferredSize.height + insets.top + insets.bottom
      val current = popup.size
      val newSize = Dimension(current.width, targetHeight)
      popup.size = newSize
      content.repaint()
    }
  }

  private fun invalidateTree(container: Container) {
    container.invalidate()
    for (child in container.components) {
      if (child is Container) invalidateTree(child) else child.invalidate()
    }
  }

  private fun openFileBrowser() {
    val file = FileChooser.chooseFile(packageFileDescriptor, project, null)
    if (file != null) mySearchField.text = file.path
  }

  private fun setupSearchListener() {
    mySearchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
      override fun insertUpdate(e: javax.swing.event.DocumentEvent) = handleTextChange()
      override fun removeUpdate(e: javax.swing.event.DocumentEvent) = handleTextChange()
      override fun changedUpdate(e: javax.swing.event.DocumentEvent) {}
    })
  }

  private fun handleTextChange() {
    val rawText = mySearchField.text
    val query = rawText.trim()
    if (query.isBlank()) {
      applyModeStrategy(DialogModeStrategy.Search)
      resultsList.updateResults(emptyList())
      versionPanel.clearSelection()
      updateViewType(ViewType.SHORT)
      collapseToInitialSize()
      return
    }
    val strategy = pickModeStrategy(rawText, isCliCommand = isCommand(rawText.trimStart()))
    applyModeStrategy(strategy)
    if (strategy is DialogModeStrategy.Search) {
      resultsList.performSearch(query)
      pendingAutoSelect?.let { name ->
        pendingAutoSelect = null
        resultsList.selectPackageByName(name)
      }
    }
  }

  /**
   * `true` only when command-mode is enabled in the registry AND the current SDK exposes a CLI
   * whose executable name is the first whitespace-separated token of [query]. With no SDK we have
   * no `cliSpecs` to match against, so the dialog falls back to search mode — the user can still
   * type the same string and see ordinary package matches.
   */
  private fun isCommand(query: String): Boolean {
    if (!Registry.`is`("python.packaging.install.dialog.command.mode", false)) return false
    val sdk = packagingService.currentSdk ?: return false
    return PythonPackageManager.forSdk(project, sdk).cliSpecs.any { query.startsWith("${it.executableName} ") }
  }

  /**
   * Single point where all mode transitions land — [pickModeStrategy] chose the strategy, this
   * method applies its [DialogModeView] to the widgets. Widget-level branching lives here (not
   * in the strategies) so the strategies stay Swing-free and testable.
   */
  private fun applyModeStrategy(strategy: DialogModeStrategy) {
    // Search re-entry short-circuit: previous inline code returned early when re-selecting the
    // same mode without a state change. Preserved so a mid-search keystroke doesn't repaint.
    if (strategy is DialogModeStrategy.Search && currentStrategy is DialogModeStrategy.Search) return

    currentStrategy = strategy
    currentMode = strategy.mode
    directInstallText = strategy.directInstallText

    val hasSelection = versionPanel.selectedPackageName != null
    val context = DialogModeContext(
      hasSelection = hasSelection,
      commandHasArgs = mySearchField.text.trim().contains(' '),
    )
    val view = strategy.computeView(context)

    listScrollPane.isVisible = view.listScrollPaneVisible
    if (view.packageInfoText != null) {
      versionPanel.packageInfoLabel.text = view.packageInfoText
      view.packageInfoIcon?.let { versionPanel.packageInfoLabel.icon = it }
      versionPanel.packageInfoLabel.isVisible = true
    }
    else {
      versionPanel.packageInfoLabel.isVisible = false
    }
    versionPanel.editableCheckbox.isVisible = view.editableVisible
    versionPanel.versionButton.isVisible = view.versionButtonVisible
    versionPanel.installButton.text = view.installButtonText
    versionPanel.installButton.isEnabled = view.installButtonEnabled
    versionPanel.installButton.isVisible = view.installButtonVisible
    bottomContainer.isVisible = view.bottomContainerVisible

    if (strategy.collapseToShort(context)) updateViewType(ViewType.SHORT)
    refreshPopupSize()
  }

  private fun performInstall() {
    when (currentMode) {
      DialogMode.DIRECT_INSTALL -> performDirectInstall()
      DialogMode.COMMAND -> performCommandExecution(mySearchField.text.trim())
      DialogMode.SEARCH -> performPackageInstall()
    }
  }

  /**
   * Runs an install/command [block] in the service scope under a fresh trace.
   *
   * [block] returns `true` on success and `false` on failure. The dialog stays open after a
   * successful install so the user can queue several packages without re-opening it; the process
   * output tool window is only popped up when the operation failed, so successful installs don't
   * pull focus away from the dialog (PY-89838 follow-up).
   */
  private fun runWithTrace(@Nls title: String, block: suspend () -> Boolean) {
    val trace = com.jetbrains.python.TraceContext(title, null)
    packagingService.serviceScope.launch {
      val success = withContext(trace) { block() }
      if (!success) {
        withContext(Dispatchers.EDT) { popup.cancel() }
        sendProcessOutputQuery(
          ProcessOutputQuery.OpenToolWindowByTraceUuid(trace.uuid.toString())
        )
      }
    }
  }

  private fun performPackageInstall() {
    val rawPackageName = versionPanel.selectedPackageName ?: return
    val packageName = PyPackageName.from(rawPackageName)
    val repository = versionPanel.selectedRepository ?: return
    val sdk = packagingService.currentSdk ?: return
    val versionToInstall = versionPanel.selectedVersion
      .takeUnless { it == message("python.packaging.install.dialog.version.latest") }
      ?.let { PyPackageVersionNormalizer.normalize(it) }
    val moduleOrProject = resolveModuleOrProject()

    runWithTrace(message("python.packaging.installing.package", rawPackageName)) {
      runInstall {
        installPackageFromRepository(
          service = packagingService,
          sdk = sdk,
          repository = repository,
          packageName = packageName,
          version = versionToInstall,
          editable = versionPanel.editableCheckbox.isSelected,
          dependencyGroup = workspaceSelector.selectedDependencyGroup,
          moduleOrProject = moduleOrProject,
        )
      }
    }
  }

  private fun performDirectInstall() {
    val sdk = packagingService.currentSdk ?: return
    val moduleOrProject = resolveModuleOrProject()

    runWithTrace(message("python.packaging.installing.package", directInstallText)) {
      runInstall {
        val uri = if (isPackageUrl(directInstallText)) URI(directInstallText) else Path.of(directInstallText).toUri()
        installPackageFromLocation(
          service = packagingService,
          sdk = sdk,
          location = uri,
          editable = versionPanel.editableCheckbox.isSelected,
          dependencyGroup = workspaceSelector.selectedDependencyGroup,
          moduleOrProject = moduleOrProject,
        )
      }
    }
  }

  /** Reports IO failures the user can fix (network/path) via the version panel toast. */
  private suspend fun runInstall(block: suspend () -> Boolean): Boolean = try {
    block()
  }
  catch (e: IOException) {
    versionPanel.notifyInstallError(message("python.packaging.install.dialog.install.failed", e.errorText()))
    false
  }

  private fun performCommandExecution(command: String) {
    if (command.isBlank()) return
    packagingService.currentSdk ?: return
    val parsed = parseCliCommand(command) ?: return
    runWithTrace(command) {
      // Only registered PyTools are runnable from the command mode — [PyTool.executeOn] handles
      // Windows `.exe`, remote / target-based SDKs consistently with the rest of the tools UI.
      // Non-PyTool tool names (e.g. raw `pip`, `uv`) fall through to ExecutableNotFound; register
      // a matching [PyTool] extension to make them invokable here.
      val moduleOrProject = resolveModuleOrProject()
      val pyTool = PyTool.findByPackageName(parsed.toolName)
      val result = if (pyTool != null) {
        runCliCommand(
          moduleOrProject = moduleOrProject,
          service = packagingService,
          tool = pyTool,
          args = parsed.args,
        )
      }
      else {
        CliCommandResult.ExecutableNotFound(parsed.toolName)
      }
      when (result) {
        is CliCommandResult.Success -> true
        is CliCommandResult.ExecutableNotFound -> {
          versionPanel.notifyInstallError(message("python.packaging.install.dialog.install.failed",
                                                  "Cannot find executable: ${result.toolName}"))
          false
        }
        is CliCommandResult.IoFailure -> {
          versionPanel.notifyInstallError(message("python.packaging.install.dialog.install.failed",
                                                  result.exception.errorText()))
          false
        }
      }
    }
  }

  private fun resolveModule(): Module? {
    workspaceSelector.selectedIJModule?.let { return it }
    val sdk = packagingService.currentSdk ?: return null
    return project.findModuleForSdk(sdk)
  }

  private fun resolveModuleOrProject(): ModuleOrProject =
    resolveModule()?.let { ModuleOrProject.ModuleAndProject(it) } ?: ModuleOrProject.ProjectOnly(project)

}

/**
 * Compact "ExceptionClass: message" formatter for install-failure toasts. Kept file-private so
 * unrelated call sites don't grow a habit of using it — the toast body is *the* only place this
 * shape is acceptable. Narrowed to [IOException] because those are the only failures we surface
 * to the user (network / filesystem); everything else is either logged or rethrown upstream.
 */
private fun IOException.errorText(): String =
  "${javaClass.simpleName}${localizedMessage?.let { ": $it" }.orEmpty()}"
