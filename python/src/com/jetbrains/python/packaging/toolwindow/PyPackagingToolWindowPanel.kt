// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.application.EDT
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.toolwindow.actions.PyTogglePackagingToolWindowAnchorAction
import com.jetbrains.python.packaging.toolwindow.details.PyPackageInfoPanel
import com.jetbrains.python.packaging.toolwindow.model.DisplayablePackage
import com.jetbrains.python.packaging.toolwindow.model.PyPackagesViewData
import com.jetbrains.python.packaging.toolwindow.modules.PyPackagesSdkController
import com.jetbrains.python.packaging.toolwindow.packages.PyPackageSearchTextField
import com.jetbrains.python.packaging.toolwindow.packages.PyPackagesListPanel
import com.jetbrains.python.packaging.toolwindow.ui.PyInstallPackageDialog
import com.jetbrains.python.packaging.toolwindow.ui.PyPackagesUiComponents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

@ApiStatus.Internal
internal class PyPackagingToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(false, true), Disposable {

  private val packageSearchController = PyPackageSearchTextField(project).also {
    Disposer.register(this, it)
  }
  private val moduleController = PyPackagesSdkController(project)
  internal val packageListController = PyPackagesListPanel(project, controller = this)
  private val infoPanel = PyPackageInfoPanel(project, onSelectInTree = { name ->
    packageListController.selectPackage(name)
  }).also {
    Disposer.register(this, it)
  }

  /**
   * Horizontal anchor (bottom/top) splits the content into a list pane and a doc preview pane so
   * the user can read package details inline without opening the standalone install dialog.
   * Vertical anchor (left/right) keeps the list-only layout because the doc preview wouldn't fit
   * legibly in a narrow side tool window. The split state survives anchor changes via the
   * dimension key, so the divider position is remembered.
   */
  private val centerSlot = JPanel(BorderLayout())
  private var lastIsHorizontal: Boolean? = null
  // Built once: the search field has a single extension and reparenting Swing components
  // automatically detaches them from the previous container, so we can reuse the same wrapper
  // across orientation changes without re-adding extensions or duplicating listeners.
  private val searchBar: JComponent by lazy { createSearchBar() }
  private val listWithSearchPanel by lazy { buildListWithSearch() }

  private val contentPanel: JPanel
  private var headerTitleRenderer: PyInterpreterHeaderTitleRenderer? = null

  internal var contentVisible: Boolean
    get() = contentPanel.isVisible
    set(value) {
      contentPanel.isVisible = value
    }

  init {
    val service = project.service<PyPackagingToolWindowService>()
    setupEmptyText()
    contentPanel = PyPackagesUiComponents.borderPanel {
      add(createContentPanel(), BorderLayout.CENTER)
    }
    setContent(contentPanel)
    setupToolWindowTitleActions()
    rebuildCenterLayout()
    trackToolWindowOrientation()
    trackModules()
    registerDisposables(service)
  }

  private fun setupEmptyText() {
    withEmptyText(message("python.toolwindow.packages.no.interpreter.text"))
  }

  private fun registerDisposables(service: PyPackagingToolWindowService) {
    Disposer.register(this, packageListController)
    Disposer.register(this, moduleController)
    Disposer.register(service, this)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PyPackagesUiComponents.SELECTED_PACKAGE_DATA_CONTEXT] = packageListController.getSelectedPackages().firstOrNull()
    sink[PyPackagesUiComponents.SELECTED_PACKAGES_DATA_CONTEXT] = this.packageListController.getSelectedPackages()
    super.uiDataSnapshot(sink)
  }

  fun getSelectedPackage(): DisplayablePackage? = packageListController.getSelectedPackages().firstOrNull()

  private fun setupToolWindowTitleActions() {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(PY_PACKAGES_TOOL_WINDOW_ID) ?: return

    // Show the default "Python Packages" id-label in the header instead of the SDK dropdown.
    // SDK can still be picked from elsewhere; the id-label is more discoverable as the title.
    toolWindow.component.putClientProperty(ToolWindowContentUi.DONT_HIDE_TOOLBAR_IN_HEADER, true)
    val gearActions = ActionManager.getInstance().getAction(ADDITIONAL_PACKAGE_TOOLBAR_ACTION_ID) as ActionGroup
    toolWindow.setAdditionalGearActions(gearActions)
    toolWindow.setTitleActions(listOf(PyTogglePackagingToolWindowAnchorAction()))

    headerTitleRenderer = PyInterpreterHeaderTitleRenderer(
      toolWindow = toolWindow,
      plainTitle = message("toolwindow.stripe.Python_Packages_Tool"),
    )
    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        SwingUtilities.invokeLater { headerTitleRenderer?.refit() }
      }
    })
  }

  private fun createContentPanel(): JComponent {
    return JPanel(BorderLayout()).apply {
      add(centerSlot, BorderLayout.CENTER)
    }
  }

  /**
   * Search field lives on top of the package list — never above the doc preview pane. In the
   * horizontal layout (bottom/top anchor) the splitter's left side is `search + list`, the right
   * side is the doc preview; in the vertical layout (side anchor) the whole content is
   * `search + list`. The search bar is reparented per layout because Swing components can only
   * have one parent at a time.
   */
  private fun rebuildCenterLayout() {
    val horizontal = isToolWindowHorizontal()
    if (lastIsHorizontal == horizontal && centerSlot.componentCount > 0) return
    lastIsHorizontal = horizontal
    centerSlot.removeAll()
    if (horizontal) {
      val splitter = OnePixelSplitter(false, "py.packages.tool.window.splitter", 0.55f).apply {
        firstComponent = listWithSearchPanel
        secondComponent = infoPanel.component
      }
      centerSlot.add(splitter, BorderLayout.CENTER)
    }
    else {
      centerSlot.add(listWithSearchPanel, BorderLayout.CENTER)
    }
    centerSlot.revalidate()
    centerSlot.repaint()
  }

  private fun buildListWithSearch(): JComponent = JPanel(BorderLayout()).apply {
    add(searchBar, BorderLayout.NORTH)
    add(packageListController.component, BorderLayout.CENTER)
  }

  private fun isToolWindowHorizontal(): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(PY_PACKAGES_TOOL_WINDOW_ID)
                     ?: return false
    val anchor = toolWindow.anchor
    return anchor == ToolWindowAnchor.BOTTOM || anchor == ToolWindowAnchor.TOP
  }

  private fun trackToolWindowOrientation() {
    project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        SwingUtilities.invokeLater { rebuildCenterLayout() }
      }
    })
  }

  private fun createSearchBar(): JComponent {
    val bundledAction = ActionManager.getInstance().getAction(PY_INSTALL_PACKAGE_ACTION_ID)
    val installAction = object : DumbAwareAction(
      message("action.PyInstallPackageAction.text"),
      null,
      PyPackageIcons.AddPackage,
    ) {
      override fun actionPerformed(e: AnActionEvent) {
        PyInstallPackageDialog(project).show(packageSearchController.text.trim().takeIf { it.isNotEmpty() })
      }
    }.apply {
      bundledAction?.shortcutSet?.let { shortcutSet = it }
    }
    val toolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.TOOLWINDOW_CONTENT,
      DefaultActionGroup(installAction),
      true,
    ).apply {
      setReservePlaceAutoPopupIcon(false)
      component.border = JBUI.Borders.empty()
      component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }
    val hPad = UIUtil.getListCellHPadding()
    val vPad = UIUtil.getListCellVPadding()
    val searchBarPanel = panel {
      row {
        cell(packageSearchController).align(AlignX.FILL).resizableColumn().customize(UnscaledGaps.EMPTY)
        cell(toolbar.component).customize(UnscaledGaps(left = hPad))
      }.customize(UnscaledGapsY.EMPTY)
    }.apply {
      border = JBUI.Borders.empty(vPad, hPad, vPad, hPad)
    }
    toolbar.targetComponent = searchBarPanel
    return searchBarPanel
  }

  private fun trackModules() {
    project.messageBus.connect(moduleController).subscribe(ModuleListener.TOPIC, object : ModuleListener {
      override fun modulesAdded(project: Project, modules: List<Module?>) = recreateModulePanel()

      override fun moduleRemoved(project: Project, module: Module) = recreateModulePanel()
    })
  }

  private fun recreateModulePanel() {
    moduleController.refreshModuleListAndSelection()
  }

  fun packageSelected(selectedPackage: DisplayablePackage?) {
    if (lastIsHorizontal == true) {
      infoPanel.setPackage(selectedPackage)
    }
  }

  fun showSearchResult(installed: List<DisplayablePackage>, repoData: List<PyPackagesViewData>) {
    packageListController.showSearchResult(installed, repoData)
  }

  fun resetSearch(installed: List<DisplayablePackage>, currentSdk: Sdk?) {
    packageListController.resetSearch(installed, currentSdk)
  }

  fun setEmpty() {
    packageListController.setLoadingState(false)
  }

  fun selectPackageName(name: String) {
    this.packageListController.selectPackage(name)
  }

  fun startLoadingSdk(@Nls sdkName: String? = null) {
    if (sdkName != null) {
      packageListController.setSdkName(sdkName)
    }
    packageListController.startSdkInit()
  }

  @RequiresEdt
  fun setInterpreterPath(path: String?) {
    headerTitleRenderer?.update(path)
  }

  internal fun setRefreshIndicatorVisible(visible: Boolean) {
    packageListController.setLoadingState(visible)
  }

  @RequiresEdt
  internal fun syncSdkControllerSelection(sdk: Sdk?) {
    moduleController.refreshAndSyncSelection(sdk)
  }

  fun syncSearchText(text: String) {
    packageSearchController.text = text
  }

  suspend fun clearFocus() {
    withContext(Dispatchers.EDT) {
      val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
      val owner = kfm.focusOwner
      if (owner != null && SwingUtilities.isDescendingFrom(owner, this@PyPackagingToolWindowPanel)) {
        kfm.clearGlobalFocusOwner()
      }
    }
  }

  fun clearSearch() {
    packageSearchController.text = ""
  }

  /**
   * One-shot "package install/uninstall finished" hook — the tool-window presenter drops the
   * search text and the focus owner. Kept as a single method so the service doesn't reach into
   * the two Swing sub-actions itself (see PY-89838 review: service must not do Swing).
   */
  suspend fun onPackageActionCompleted() {
    clearSearch()
    clearFocus()
  }

  override fun dispose() {}

  companion object {
    internal const val PY_PACKAGES_TOOL_WINDOW_ID: String = "Python Packages"

    @Language("devkit-action-id")
    private const val ADDITIONAL_PACKAGE_TOOLBAR_ACTION_ID = "PyPackageToolbarAdditional"

    @Language("devkit-action-id")
    private const val PY_INSTALL_PACKAGE_ACTION_ID = "PyInstallPackageAction"
  }
}
