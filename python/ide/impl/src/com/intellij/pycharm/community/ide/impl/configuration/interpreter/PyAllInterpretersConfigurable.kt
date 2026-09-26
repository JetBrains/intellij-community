// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.hover.ListHoverListener
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.insets
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.application.EDT
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.asItem
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.pythonInterpreterAsync
import com.intellij.python.sdk.common.PyInterpreterItem
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyInterpreterEditDialogLauncher
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.configuration.PyInterpreterPathsDialogLauncher
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowPanel
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.associatedModuleNioPath
import com.jetbrains.python.sdk.collectAddInterpreterActions
import com.jetbrains.python.sdk.filterAssignablePythonSdks
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.HyperlinkEvent

/**
 * "Python | All Interpreters" settings page introduced by the interpreter settings redesign (PY-89840).
 */
internal class PyAllInterpretersConfigurable(private val project: Project) : SearchableConfigurable {

  private val projectSdksModel = ProjectSdksModel()
  private val listModel = CollectionListModel<PythonInterpreter>()
  private val rowActionsState = RowActionsState()

  /**
   * Selection snapshot updated on EDT by the list's own `ListSelectionListener` and read by toolbar
   * actions on BGT. `@Volatile` so the read sees the latest write without a memory barrier of its
   * own. Toolbar actions can therefore declare `ActionUpdateThread.BGT` — the reviewer-requested
   * default — without touching Swing state during `update`.
   */
  @Volatile private var currentlySelectedInterpreter: PythonInterpreter? = null

  private val interpretersList: JBList<PythonInterpreter> = object : JBList<PythonInterpreter>(listModel) {
    // Track the viewport width so long paths do not push the list wider than its scroll pane and cause the
    // parent Settings row to expand and clip content when the filter/search changes the row count.
    override fun getScrollableTracksViewportWidth(): Boolean = true
  }.apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = PyInterpreterNameAndVersionRenderer(this, rowActionsState)
    emptyText.text = PyBundle.message("configurable.PyAllInterpretersConfigurable.empty.text")
    selectionBackground = background
    visibleRowCount = DEFAULT_VISIBLE_ROWS
    ListHoverListener.DEFAULT.addTo(this)
    addListSelectionListener { currentlySelectedInterpreter = selectedValue }
    val handler = RowActionsMouseHandler(this, rowActionsState, ::copyInterpreterPath)
    addMouseListener(handler)
    addMouseMotionListener(handler)
  }

  /** Snapshot of interpreters after the "hide other projects" filter, before the search-field filter. */
  private var currentSnapshot: List<PythonInterpreter> = emptyList()
  private var hideOthers: Boolean = true
  private var searchQuery: String = ""

  /**
   * Root component of the page; captured in [createComponent] so link handlers can look up the enclosing
   * `Settings` dialog through the current data context. `lateinit` because the platform calls
   * [createComponent] after the constructor and before any hyperlink can fire.
   */
  private lateinit var rootComponent: JComponent

  override fun getId(): String = ID

  override fun getDisplayName(): String = PyBundle.message("configurable.PyAllInterpretersConfigurable.display.name")

  override fun createComponent(): JComponent {
    val hintText = PyBundle.message(
      "configurable.PyAllInterpretersConfigurable.hint",
      PyBundle.message(
        if (isMultiModule()) "configurable.PyAllInterpretersConfigurable.hint.link.workspace"
        else "configurable.PyAllInterpretersConfigurable.hint.link.project"
      ),
    )
    val component = panel {
      row {
        text(hintText) { event ->
          if (event.eventType != HyperlinkEvent.EventType.ACTIVATED) return@text
          when (event.description) {
            HINT_LINK_WORKSPACE -> navigateToWorkspaceStructure()
            HINT_LINK_PACKAGES -> closeDialogAndFocusPackagesToolWindow()
          }
        }.align(Align.FILL).resizableColumn()
      }

      row {
        cell(buildToolbarAndList()).align(Align.FILL).resizableColumn()
      }.topGap(TopGap.MEDIUM).resizableRow()
    }.apply {
      val insets = UIUtil.PANEL_REGULAR_INSETS
      border = JBUI.Borders.empty(insets.top, insets.left, 0, insets.right)
    }
    rootComponent = component
    return component
  }

  private fun isMultiModule(): Boolean = ModuleManager.getInstance(project).modules.size > 1

  private fun navigateToWorkspaceStructure() {
    val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(rootComponent)) ?: return
    val configurable = settings.find(PyWorkspaceStructureConfigurable.ID) ?: return
    settings.select(configurable)
  }

  private fun closeDialogAndFocusPackagesToolWindow() {
    // `PY_PACKAGES_TOOL_WINDOW_ID` is registered by this plugin's own `<toolWindow>` in
    // `intellij.pycharm.community.ide.impl.xml`, so the lookup is null only when the plugin XML
    // is broken (missing registration, renamed id) — a real bug, not a graceful state. `!!` fails
    // loudly so the invariant break surfaces in the exception reporter instead of silently
    // dropping the "focus packages tool window" action.
    val toolWindow = ToolWindowManager.getInstance(project)
      .getToolWindow(PyPackagingToolWindowPanel.PY_PACKAGES_TOOL_WINDOW_ID)!!
    SwingUtilities.getWindowAncestor(rootComponent)?.dispose()
    PyPackageCoroutine.launch(project) {
      withContext(Dispatchers.EDT) {
        toolWindow.activate(null, true, true)
      }
    }
  }

  private fun buildToolbarAndList(): JComponent {
    val actionGroup = DefaultActionGroup().apply {
      add(AddInterpreterAction())
      add(RemoveInterpreterAction())
      addSeparator()
      add(HideOthersToggleAction())
      add(EditInterpreterAction())
      add(ShowInterpreterPathsAction())
    }
    val toolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, true)
      .apply { targetComponent = interpretersList }

    val searchField = SearchTextField(false).apply {
      textEditor.emptyText.text = PyBundle.message("configurable.PyAllInterpretersConfigurable.search.placeholder")
      textEditor.columns = SEARCH_COLUMNS
      addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          searchQuery = text
          applySearchFilter()
        }
      })
    }

    val topBar = JPanel(BorderLayout()).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
      add(toolbar.component, BorderLayout.WEST)
      add(searchField, BorderLayout.EAST)
    }

    val scrollPane = JBScrollPane(interpretersList).apply {
      border = JBUI.Borders.empty()
      minimumSize = Dimension(0, 0)
      preferredSize = Dimension(0, 0)
    }

    return JPanel(BorderLayout()).apply {
      border = IdeBorderFactory.createBorder()
      add(topBar, BorderLayout.NORTH)
      add(scrollPane, BorderLayout.CENTER)
    }
  }

  /**
   * Signals that at least one editable SDK was mutated through a per-row dialog
   * ([PyInterpreterEditDialogLauncher] / [PyInterpreterPathsDialogLauncher]) since the last apply.
   *
   * `ProjectSdksModel.isModified` only flips on add / remove / rename via the model's own API;
   * committing a `SdkModificator` on an editable clone bypasses that flag. Without this manual
   * signal the outer Settings **Apply** button stays disabled and the editable-to-registered
   * propagation in [apply] never runs — closing Settings would then throw the user's edit away
   * (`reset` re-clones editable copies from the registered SDK table on next open).
   */
  private var perRowSdkModified: Boolean = false

  internal fun markSdkModifiedFromRowAction() {
    perRowSdkModified = true
  }

  override fun isModified(): Boolean = projectSdksModel.isModified || perRowSdkModified

  override fun reset() {
    projectSdksModel.reset(project)
    perRowSdkModified = false
    reloadList()
  }

  override fun apply() {
    projectSdksModel.apply(null)
    perRowSdkModified = false
  }

  override fun disposeUIResources() {
    projectSdksModel.disposeUIResources()
  }

  private fun reloadList(preferredSelection: Sdk? = null) {
    val previouslySelected = (preferredSelection?.name ?: selectedInterpreter()?.sdk?.name)?.let(::PySdkName)
    val allEditable = project.filterAssignablePythonSdks(projectSdksModel.sdks.toList(), null)
    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      val interpreters = allEditable.map { it.pythonInterpreterAsync() }
      val moduleBasePaths = project.getPyProjects().mapTo(HashSet()) { it.baseDir }
      val visible = filterInterpreters(interpreters, hideOthers, moduleBasePaths)
      withContext(Dispatchers.EDT) {
        currentSnapshot = visible
        applySearchFilter()
        val pickedName = pickSelection(listModel.items.map { PySdkName(it.sdk.name) }, previouslySelected)
        val toSelect = pickedName?.let { name -> listModel.items.firstOrNull { PySdkName(it.sdk.name) == name } }
        if (toSelect != null) interpretersList.setSelectedValue(toSelect, true)
      }
    }
  }

  private fun applySearchFilter() {
    listModel.replaceAll(filterBySearchQuery(currentSnapshot, searchQuery))
  }

  /** BGT-safe. See [currentlySelectedInterpreter]. */
  private fun selectedInterpreter(): PythonInterpreter? = currentlySelectedInterpreter

  private inner class AddInterpreterAction : DumbAwareAction(
    PyBundle.messagePointer("python.interpreters.add.interpreter.action.text"),
    Presentation.NULL_STRING,
    AllIcons.General.Add,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      val group = DefaultActionGroup().apply {
        addAll(collectAddInterpreterActions(ModuleOrProject.ProjectOnly(project)) { newSdk ->
          projectSdksModel.addSdk(newSdk)
          reloadList(preferredSelection = newSdk)
        })
        // Optional third-party extras (AI Assistant / Toolbox / others). The group is declared
        // empty in `intellij.python.community.impl.xml`; other plugins inject their own
        // "Add interpreter" actions via `<add-to-group group-id="Python.NewInterpreter.Extra">`.
        // `null` = no plugin extended it → nothing to add, popup renders without the extras.
        ActionManager.getInstance().getAction("Python.NewInterpreter.Extra")?.let { add(it) }
      }
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(
        null, group, e.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false,
      )
      val source = e.inputEvent?.component
      if (source != null) popup.showUnderneathOf(source)
      else popup.showInBestPositionFor(e.dataContext)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  /**
   * Toolbar action that operates on the currently selected interpreter. Handles the "no selection →
   * disabled" state and the BGT dispatch once, so a concrete subclass only supplies its `perform`
   * body. `getSelected` reads the volatile [currentlySelectedInterpreter], so `update` stays off
   * the EDT per reviewer feedback (formerly `ActionUpdateThread.EDT`).
   */
  private abstract inner class SelectedInterpreterAction(
    text: java.util.function.Supplier<String>,
    icon: Icon,
  ) : DumbAwareAction(text, Presentation.NULL_STRING, icon) {
    final override fun actionPerformed(e: AnActionEvent) {
      val interpreter = selectedInterpreter() ?: return
      perform(interpreter)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = selectedInterpreter() != null
    }

    final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    protected abstract fun perform(interpreter: PythonInterpreter)
  }

  private inner class RemoveInterpreterAction : SelectedInterpreterAction(
    PyBundle.messagePointer("python.interpreters.remove.interpreter.action.text"),
    AllIcons.General.Remove,
  ) {
    override fun perform(interpreter: PythonInterpreter) {
      interpreter.removeFrom(projectSdksModel)
      reloadList()
    }
  }

  /**
   * Opens the legacy interpreter-paths dialog via [PyInterpreterPathsDialogLauncher] — same
   * "sys.path add / remove / reload" surface the classical Interpreter Settings dialog offers
   * from its "Show Paths" toolbar button, preserved verbatim here so path edits keep the same
   * commit / reload flow.
   */
  private inner class ShowInterpreterPathsAction : SelectedInterpreterAction(
    PyBundle.messagePointer("python.interpreters.show.interpreter.paths.text"),
    AllIcons.Actions.ShowAsTree,
  ) {
    override fun perform(interpreter: PythonInterpreter) {
      PyPackageCoroutine.launch(project) {
        interpreter.openPathsDialog(project) {
          markSdkModifiedFromRowAction()
          reloadList(preferredSelection = it)
        }
      }
    }
  }

  /**
   * Opens the "Interpreter Settings" edit form via [PyInterpreterEditDialogLauncher]. Enabled only
   * for target-aware remote SDKs (Docker / SSH / WSL / …), which need the target-specific dialog
   * to change the target configuration. Local SDKs are edited through the module-scoped Workspace
   * Structure page instead — the "Associate with module" choice only makes sense in a module
   * context, and this page is project-scoped.
   */
  private inner class EditInterpreterAction : SelectedInterpreterAction(
    PyBundle.messagePointer("configurable.PyAllInterpretersConfigurable.edit.interpreter"),
    AllIcons.Actions.Edit,
  ) {
    override fun update(e: AnActionEvent) {
      val interpreter = selectedInterpreter()
      e.presentation.isEnabled = interpreter != null && interpreter.sdk.sdkAdditionalData is PyTargetAwareAdditionalData
    }

    override fun perform(interpreter: PythonInterpreter) {
      val editor = interpreter.editorFor(project, this@PyAllInterpretersConfigurable) ?: return
      if (editor.showAndGet()) {
        markSdkModifiedFromRowAction()
        reloadList()
      }
    }
  }

  private inner class HideOthersToggleAction : DumbAwareToggleAction(
    PyBundle.messagePointer("sdk.details.dialog.hide.all.virtual.envs"),
    Presentation.NULL_STRING,
    AllIcons.General.Filter,
  ) {
    override fun isSelected(e: AnActionEvent): Boolean = hideOthers

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      hideOthers = state
      reloadList()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  private fun copyInterpreterPath(interpreter: PythonInterpreter) {
    val path = interpreter.sdk.homePath ?: return
    CopyPasteManager.getInstance().setContents(StringSelection(path))
  }

  companion object {
    const val ID: String = "com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyAllInterpretersConfigurable"

    private const val HINT_LINK_WORKSPACE = "workspace"
    private const val HINT_LINK_PACKAGES = "packages"
    private const val SEARCH_COLUMNS = 20
    private const val DEFAULT_VISIBLE_ROWS = 12
  }
}

private const val COPY_PATH_TOOLTIP_KEY = "configurable.PyAllInterpretersConfigurable.row.action.copy.path"

/**
 * Renders a [PyInterpreterItem] into [label] as the "All Interpreters" list row draws it:
 * name (or `[marker] name` when the interpreter is flagged) + optional `suffix` + description +
 * tooltip carrying the problem's reason. The icon is set by the caller so the label-text path
 * stays pure.
 */
internal fun renderPyInterpreterItemText(label: SimpleColoredComponent, item: PyInterpreterItem) {
  val problem = item.problem
  if (problem != null) label.append("[${problem.marker}] ${item.name}", SimpleTextAttributes.ERROR_ATTRIBUTES)
  else label.append(item.name)
  item.suffix?.let { label.append(" $it", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES) }
  label.append("  ${item.description}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
  label.toolTipText = problem?.reason
}

/**
 * Hover / press state for the single Copy-path icon, shared between the mouse handler (writes) and
 * the renderer (reads). The renderer is a stamp with no persistent event stream, so its "is this
 * icon hovered" question is answered by the handler-owned latest row.
 */
internal class RowActionsState {
  var hoveredRow: Int = -1
    private set
  var pressedRow: Int = -1
    private set

  fun setHover(row: Int): Boolean {
    if (row == hoveredRow) return false
    hoveredRow = row
    return true
  }

  fun setPressed(row: Int): Boolean {
    if (row == pressedRow) return false
    pressedRow = row
    return true
  }
}

/**
 * Translates mouse gestures on the list into row lookups against the renderer's icon geometry,
 * updates [RowActionsState], repaints the affected row, adjusts the cursor, and finally routes a
 * click to [onCopyPath]. Press → release on the same row to fire, matching standard button UX.
 */
private class RowActionsMouseHandler(
  private val list: JBList<PythonInterpreter>,
  private val state: RowActionsState,
  private val onCopyPath: (PythonInterpreter) -> Unit,
) : MouseAdapter() {

  override fun mouseMoved(e: MouseEvent) = updateHover(e)
  override fun mouseDragged(e: MouseEvent) = updateHover(e)
  override fun mouseEntered(e: MouseEvent) = updateHover(e)

  override fun mouseExited(e: MouseEvent) {
    if (state.setHover(-1)) list.repaint()
    list.cursor = Cursor.getDefaultCursor()
  }

  override fun mousePressed(e: MouseEvent) {
    if (!SwingUtilities.isLeftMouseButton(e)) return
    val row = hitTest(e) ?: return
    if (state.setPressed(row)) repaintRow(row)
    e.consume()
  }

  override fun mouseReleased(e: MouseEvent) {
    if (!SwingUtilities.isLeftMouseButton(e)) return
    val pressedRow = state.pressedRow
    if (state.setPressed(-1) && pressedRow >= 0) repaintRow(pressedRow)
    val row = hitTest(e) ?: return
    if (row == pressedRow) {
      list.model.getElementAt(row)?.let(onCopyPath)
      e.consume()
    }
  }

  private fun updateHover(e: MouseEvent) {
    val row = hitTest(e) ?: -1
    if (state.setHover(row)) list.repaint()
    list.cursor = if (row >= 0) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
    list.toolTipText = if (row >= 0) PyBundle.message(COPY_PATH_TOOLTIP_KEY) else null
  }

  private fun hitTest(e: MouseEvent): Int? {
    val row = list.locationToIndex(Point(e.x, e.y)).takeIf { it >= 0 } ?: return null
    val bounds = list.getCellBounds(row, row) ?: return null
    if (!bounds.contains(e.x, e.y)) return null
    return row.takeIf { rowActionRect(bounds).contains(e.x, e.y) }
  }

  private fun repaintRow(row: Int) {
    val b = list.getCellBounds(row, row) ?: return
    list.repaint(b)
  }
}

/**
 * Icon geometry for the single Copy-path action, shared between the renderer (paint) and the mouse
 * handler (hit-test) so both sides read the same trailing offsets from the same platform
 * accessors — no drift between where the icon paints and where the click lands. Coordinates are in
 * list-space (relative to the JList itself); the renderer converts back to its inner-panel space.
 */
private fun rowActionRect(rowBounds: Rectangle): Rectangle {
  val rowInsetH = rowSelectionBorderInsets().right
  val trailingPad = JBUI.scale(RowActionMetrics.TRAILING_INSET)
  val iconW = RowActionMetrics.ICON_SIZE
  val iconH = RowActionMetrics.ICON_SIZE
  val y = rowBounds.y + (rowBounds.height - iconH) / 2
  val copyX = rowBounds.x + rowBounds.width - rowInsetH - trailingPad - iconW
  return Rectangle(copyX, y, iconW, iconH)
}

/**
 * Returns the border insets the renderer's [SelectablePanel] wraps around its inner content — same recipe
 * used by [com.intellij.ui.dsl.listCellRenderer.impl.LcrRowImpl] so the icon geometry follows platform LaF
 * and stays in sync with the selection band.
 */
private fun rowSelectionBorderInsets(): java.awt.Insets {
  val leftRight = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.get()
  val inner = JBUI.CurrentTheme.Popup.Selection.innerInsets()
  return insets(0, inner.left + leftRight, 0, inner.right + leftRight)
}

private object RowActionMetrics {
  /**
   * Icon slot dimensions — resolved from the actual icons rather than hardcoded 16px so HiDPI and
   * theme overrides drive the layout. Both trailing icons are the same size in practice.
   */
  val ICON_SIZE: Int
    get() = AllIcons.Actions.Copy.iconWidth
  /** Gap between the two trailing icons — matches the standard actions-list cell padding. */
  val GAP: Int
    get() = JBUI.CurrentTheme.ActionsList.cellPadding().left
  /** Space between the last icon and the row's inner selection edge — reuses the standard focus width. */
  val TRAILING_INSET: Int
    get() = BW.get()
  /** Extra pad around each icon inside its hover / pressed background rectangle. Same value. */
  val HOVER_PAD: Int
    get() = BW.get()
}

/**
 * The [Sdk] behind [PythonInterpreter], for the redesigned page's per-row edit / remove / paths flows
 * that still bottom out in `Sdk`-typed APIs (`ProjectSdksModel`, `PyInterpreterEditDialogLauncher`, …).
 * The `getSdkAPI` bridge is deprecated by design; this single file-local accessor keeps the
 * suppression in one place instead of scattering it across every call site.
 */
private val PythonInterpreter.sdk: Sdk
  get() = getSdkAPI()

/** Removes the underlying SDK from [model]. */
internal fun PythonInterpreter.removeFrom(model: ProjectSdksModel) {
  model.removeSdk(sdk)
}

/**
 * Returns the "Interpreter Settings" edit dialog for this interpreter, or `null` when it is a
 * legacy remote SDK that carries no target configuration. Callers use the same `null` to disable
 * the Edit button and to skip the click.
 *
 * The "All Interpreters" page is project-scoped, so a "current module" is not obvious for a
 * multi-module project. The first module is passed as the association target — the reviewer-approved
 * fallback for this page. The module-scoped "Workspace Structure" page passes its own module.
 */
internal fun PythonInterpreter.editorFor(project: Project, parent: Configurable): DialogWrapper? {
  val associationTarget = ModuleManager.getInstance(project).modules.firstOrNull()
  return PyInterpreterEditDialogLauncher.editorFor(project, this, associationTarget, parent)
}

/** Opens the legacy interpreter-paths dialog for this interpreter and reports the mutated SDK. */
internal suspend fun PythonInterpreter.openPathsDialog(project: Project, onCommit: (Sdk) -> Unit) {
  PyInterpreterPathsDialogLauncher.open(project, sdk, onCommit)
}

/**
 * Pure filter for the "hide interpreters that belong to another project" toggle. Passing `hideOthers = false`
 * keeps the full list; otherwise an interpreter is kept when it has no association or when its association
 * matches one of the project's module base dirs. Association writes go through `setAssociationToModule`, so
 * the association path is always a module base — no project-base fallback.
 */
internal fun filterInterpreters(
  interpreters: List<PythonInterpreter>,
  hideOthers: Boolean,
  moduleBasePaths: Set<Path>,
): List<PythonInterpreter> {
  if (!hideOthers) return interpreters
  return interpreters.filter { interpreter ->
    val associated = interpreter.sdk.associatedModuleNioPath ?: return@filter true
    associated in moduleBasePaths
  }
}

/** Typed wrapper for a [com.intellij.openapi.projectRoots.Sdk] name — lets the selection API and its test speak in the same currency, without a raw `String`. */
@JvmInline
internal value class PySdkName(val value: String)

/**
 * Chooses which SDK to keep selected after a reload. Prefers [previouslySelected]; falls back to
 * the first row. Strong-typed on [PySdkName] end-to-end so a rename or a stray String does not
 * silently slip through, and [PyAllInterpretersSelectionTest] pins every branch without a fixture.
 */
internal fun pickSelection(candidates: List<PySdkName>, previouslySelected: PySdkName?): PySdkName? =
  candidates.firstOrNull { it == previouslySelected } ?: candidates.firstOrNull()

/** Pure substring filter over `sdkName` / `homePath`. Case-insensitive; empty query keeps every row. */
internal fun filterBySearchQuery(interpreters: List<PythonInterpreter>, query: String): List<PythonInterpreter> {
  val q = query.trim().lowercase()
  if (q.isEmpty()) return interpreters
  return interpreters.filter { interpreter ->
    interpreter.sdk.name.lowercase().contains(q) || (interpreter.sdk.homePath?.lowercase()?.contains(q) == true)
  }
}

internal class PyAllInterpretersConfigurableProvider(private val project: Project) : ConfigurableProvider() {
  override fun canCreateConfigurable(): Boolean = PyInterpreterRedesignFlags.isEnabled()

  override fun createConfigurable(): Configurable = PyAllInterpretersConfigurable(project)
}

/**
 * Renders each row as `<icon> <name>  <version>` (version in grey), with inline hover/selection actions on
 * the trailing side: **Copy path** and **Open Packages Tool Window for this interpreter**.
 *
 * Selection paints as a rounded-corner rectangle (per PY-89840 design) via [SelectablePanel]. Panel is
 * opaque and always paints the list background first so `JList`'s default full-width selection band (drawn
 * under the renderer) is fully covered by our own rounded rectangle.
 *
 * Action icons are drawn directly in [InnerPanel.paint] after the label paints so both icons and their
 * hover/pressed backgrounds live above the row content. Positions come from [rowActionRects] so that the
 * renderer and [RowActionsMouseHandler] see identical geometry — the previous JLabel + FlowLayout strip
 * (icons unaligned, no hover feedback, distance-from-right hit test) drifted between painting and clicks.
 */
private class PyInterpreterNameAndVersionRenderer(
  private val list: JBList<PythonInterpreter>,
  private val state: RowActionsState,
) : ListCellRenderer<PythonInterpreter> {
  private val label = SimpleColoredComponent().apply { isOpaque = false }

  private var currentRow: Int = -1
  private var actionsVisible: Boolean = false

  private inner class InnerPanel : JPanel(BorderLayout()) {
    init {
      isOpaque = false
      add(label, BorderLayout.CENTER)
      // Reserve space on the trailing edge for the single action icon so the label never paints under it.
      val reserve = RowActionMetrics.TRAILING_INSET +
                    RowActionMetrics.ICON_SIZE +
                    RowActionMetrics.HOVER_PAD * 2
      border = JBUI.Borders.emptyRight(JBUI.scale(reserve))
    }

    override fun paint(g: Graphics) {
      super.paint(g)
      if (!actionsVisible) return
      val rowBounds = list.getCellBounds(currentRow, currentRow) ?: return
      val insets = rowSelectionBorderInsets()
      val rect = rowActionRect(rowBounds)
      paintCopyPathIcon(g, rect.x - rowBounds.x - insets.left, rect.y - rowBounds.y - insets.top)
    }

    private fun paintCopyPathIcon(g: Graphics, x: Int, y: Int) {
      val icon = AllIcons.Actions.Copy
      val isHovered = state.hoveredRow == currentRow
      val isPressed = state.pressedRow == currentRow
      if (isHovered || isPressed) paintHoverBackground(g, x, y, icon.iconWidth, icon.iconHeight, isPressed)
      icon.paintIcon(this, g, x, y)
    }

    private fun paintHoverBackground(g: Graphics, x: Int, y: Int, iconW: Int, iconH: Int, pressed: Boolean) {
      val pad = JBUI.scale(RowActionMetrics.HOVER_PAD)
      val color = if (pressed) JBUI.CurrentTheme.ActionButton.pressedBackground()
                  else JBUI.CurrentTheme.ActionButton.hoverBackground()
      val rect = Rectangle(x - pad, y - pad, iconW + pad * 2, iconH + pad * 2)
      ActionButtonLook.SYSTEM_LOOK.paintLookBackground(g, rect, color)
    }
  }

  private val inner = InnerPanel()

  private val panel: SelectablePanel = SelectablePanel.wrap(inner).apply {
    val leftRightInset = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.unscaled.toInt()
    val innerInsets = JBUI.CurrentTheme.Popup.Selection.innerInsets().unscaled
    isOpaque = true
    selectionArc = JBUI.CurrentTheme.Popup.Selection.ARC.get()
    selectionInsets = JBInsets.create(0, leftRightInset)
    border = JBUI.Borders.empty(0, innerInsets.left + leftRightInset, 0, innerInsets.right + leftRightInset)
  }

  override fun getListCellRendererComponent(
    list: JList<out PythonInterpreter>?,
    value: PythonInterpreter?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    currentRow = index
    label.clear()
    if (value != null) {
      val item: PyInterpreterItem = value.asItem()
      label.icon = item.icon.icon()
      renderPyInterpreterItemText(label, item)
    }
    val bg = list?.background ?: UIUtil.getListBackground(false, false)
    panel.background = bg
    val hovered = list != null && ListHoverListener.getHoveredIndex(list) == index
    panel.selectionColor = when {
      isSelected -> UIUtil.getListSelectionBackground(cellHasFocus)
      hovered -> JBUI.CurrentTheme.List.Hover.background(cellHasFocus)
      else -> null
    }
    actionsVisible = isSelected || hovered
    return panel
  }

}
