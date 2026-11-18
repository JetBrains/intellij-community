// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration.interpreter

import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled
import com.intellij.python.sdk.backend.PythonInterpreterEntry
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.impl.presentation
import com.intellij.python.sdk.backend.impl.PythonInterpreterPresentation
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.EDT
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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.python.sdk.backend.associatedModuleDir
import com.intellij.python.sdk.backend.binaryPathString
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.PopupHandler
import com.intellij.ui.SearchTextField
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.hover.ListHoverListener
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.insets
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyInterpreterEditDialogLauncher
import com.jetbrains.python.configuration.PyInterpreterPathsDialogLauncher
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowPanel
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.HyperlinkEvent
import org.jetbrains.annotations.Nls

/**
 * "Python | All Interpreters" settings page introduced by the interpreter settings redesign (PY-89840).
 */
internal class PyAllInterpretersConfigurable(private val project: Project) : SearchableConfigurable {

  private val projectSdksModel = ProjectSdksModel()
  private val listModel = CollectionListModel<PyInterpreterRow>()

  /**
   * Selection snapshot updated on EDT by the list's own `ListSelectionListener` and read by toolbar
   * actions on BGT. `@Volatile` so the read sees the latest write without a memory barrier of its
   * own. Toolbar actions can therefore declare `ActionUpdateThread.BGT` — the reviewer-requested
   * default — without touching Swing state during `update`.
   */
  @Volatile private var currentlySelectedRow: PyInterpreterRow? = null

  private val interpretersList: JBList<PyInterpreterRow> = object : JBList<PyInterpreterRow>(listModel) {
    // Track the viewport width so long paths do not push the list wider than its scroll pane and cause the
    // parent Settings row to expand and clip content when the filter/search changes the row count.
    override fun getScrollableTracksViewportWidth(): Boolean = true

    // A group header is not a row: a click on it selects nothing.
    override fun processMouseEvent(e: MouseEvent) {
      if (isOnGroupHeader(this, e.point)) return
      // The context menu acts on the selected row, so a right click selects the row under the mouse first.
      if (e.id == MouseEvent.MOUSE_PRESSED && SwingUtilities.isRightMouseButton(e)) {
        locationToIndex(e.point).takeIf { it >= 0 && getCellBounds(it, it)?.contains(e.point) == true }?.let { selectedIndex = it }
      }
      super.processMouseEvent(e)
    }
  }.apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = PyInterpreterNameAndVersionRenderer()
    emptyText.text = PyBundle.message("configurable.PyAllInterpretersConfigurable.empty.text")
    selectionBackground = background
    visibleRowCount = DEFAULT_VISIBLE_ROWS
    ListHoverListener.DEFAULT.addTo(this)
    addListSelectionListener { currentlySelectedRow = selectedValue }
  }

  /** Snapshot of interpreters after the "hide other projects" filter, before the search-field filter. */
  private var currentSnapshot: List<PyInterpreterRow> = emptyList()
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
    // `intellij.python.community.impl.xml`, so the lookup is null only when the plugin XML
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
    // No Add here: every interpreter belongs to a PyProject, and this page has none. Add one from a module.
    val remove = RemoveInterpreterAction()
    val edit = EditInterpreterAction()
    val showPaths = ShowInterpreterPathsAction()
    val copyPath = CopyInterpreterPathAction()
    val actionGroup = DefaultActionGroup().apply {
      add(remove)
      addSeparator()
      add(HideOthersToggleAction())
      add(edit)
      add(showPaths)
      add(copyPath)
    }
    // The toolbar is not in the Tab order, so the list reaches its actions from the keyboard: by shortcut, and by the
    // context menu (Shift+F10). A tooltip of a toolbar button shows its shortcut.
    remove.registerCustomShortcutSet(CommonShortcuts.getDelete(), interpretersList)
    edit.registerCustomShortcutSet(CommonShortcuts.getEditSource(), interpretersList)
    showPaths.registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)), interpretersList)
    copyPath.registerCustomShortcutSet(CommonShortcuts.getCopy(), interpretersList)
    PopupHandler.installPopupMenu(interpretersList, DefaultActionGroup(edit, showPaths, copyPath, Separator.getInstance(), remove), ActionPlaces.POPUP)
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

  // Add / Remove / Edit flows mutate `ProjectJdkTable` and real SDKs directly, so there is nothing
  // pending in `projectSdksModel` to apply or revert — the model only serves as a lazy source for
  // the list and gets re-read via `reset(project)` whenever the JDK table or the per-row dialogs
  // change state.
  override fun isModified(): Boolean = perRowSdkModified

  override fun reset() {
    projectSdksModel.reset(project)
    perRowSdkModified = false
    reloadList()
  }

  override fun apply() {
    perRowSdkModified = false
  }

  override fun disposeUIResources() {
    projectSdksModel.disposeUIResources()
  }

  private fun reloadList() {
    val previouslySelected = selectedRow()?.selectionKey
    PyPackageCoroutine.launch(project, Dispatchers.IO) {
      // An entry that names no interpreter gets a row that says why, and the user can remove it.
      // The interpreters a project uses: this page is a history of environments, so they cannot be removed here.
      val snapshot = EvoPyProjectModel.getInstance(project).snapshot()
      val rows = PythonInterpreterRegistry.getInstance(project).interpreterEntries().map { entry ->
        when (entry) {
          is PythonInterpreterEntry.Available -> PyInterpreterRow.Interpreter(
            entry,
            inUse = snapshot.isInUse(entry.interpreter),
            owner = snapshot.ownerNameOf(entry.interpreter),
          )
          is PythonInterpreterEntry.Unavailable -> PyInterpreterRow.Broken(entry)
        }
      }
      val moduleBasePaths = project.getPyProjects().mapTo(HashSet()) { it.baseDir }
      val ownerOrder = snapshot.evoPyProjects.map { it.pyProject.residesOnModule.name }.toList()
      val showGroups = snapshot.workspaces.size > 1
      val visible = groupByOwner(filterInterpreters(rows, hideOthers, moduleBasePaths), ownerOrder)
      withContext(Dispatchers.EDT) {
        interpretersList.putClientProperty(SHOW_GROUPS, showGroups)
        currentSnapshot = visible
        applySearchFilter()
        val pickedKey = pickSelection(listModel.items.map { it.selectionKey }, previouslySelected)
        val toSelect = pickedKey?.let { key -> listModel.items.firstOrNull { it.selectionKey == key } }
        if (toSelect != null) interpretersList.setSelectedValue(toSelect, true)
      }
    }
  }

  private fun applySearchFilter() {
    listModel.replaceAll(filterBySearchQuery(currentSnapshot, searchQuery))
  }

  /** BGT-safe. See [currentlySelectedRow]. */
  private fun selectedRow(): PyInterpreterRow? = currentlySelectedRow

  /** The interpreter of the selected row, or `null` for no selection or a broken SDK. BGT-safe. */
  private fun selectedInterpreter(): PythonInterpreter? = (currentlySelectedRow as? PyInterpreterRow.Interpreter)?.interpreter

  /**
   * Toolbar action that operates on the currently selected interpreter. Handles the "no selection →
   * disabled" state and the BGT dispatch once, so a concrete subclass only supplies its `perform`
   * body. `getSelected` reads the volatile [currentlySelectedRow], so `update` stays off
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

  /** Removes the SDK of the selected row, see [removeRow]. */
  private inner class RemoveInterpreterAction : DumbAwareAction(
    PyBundle.messagePointer("python.interpreters.remove.interpreter.action.text"),
    Presentation.NULL_STRING,
    // The trash can of the new UI.
    AllIcons.Actions.GC,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      selectedRow()?.let(::removeRow)
    }

    override fun update(e: AnActionEvent) {
      val row = selectedRow()
      val inUse = row?.isInUse() == true
      e.presentation.isEnabled = row != null && !inUse
      e.presentation.description = if (inUse) PyBundle.message("python.interpreters.remove.in.use.description") else null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  /** Opens the interpreter-paths dialog via [PyInterpreterPathsDialogLauncher]: add, remove and reload `sys.path` entries. */
  private inner class ShowInterpreterPathsAction : SelectedInterpreterAction(
    PyBundle.messagePointer("python.interpreters.show.interpreter.paths.text"),
    AllIcons.Actions.ShowAsTree,
  ) {
    override fun perform(interpreter: PythonInterpreter) = showPaths(interpreter)
  }

  /**
   * Opens the "Interpreter Settings" edit form via [PyInterpreterEditDialogLauncher]. Shown only
   * while a target-aware remote SDK (Docker / SSH / WSL / …) is selected, which needs the
   * target-specific dialog to change the target configuration. Local SDKs are edited through the
   * module-scoped Workspace Structure page instead — the "Associate with module" choice only makes
   * sense in a module context, and this page is project-scoped. Hidden rather than disabled
   * (PY-93031): nothing on this page can make the button available for a local SDK.
   */
  private inner class EditInterpreterAction : SelectedInterpreterAction(
    PyBundle.messagePointer("configurable.PyAllInterpretersConfigurable.edit.interpreter"),
    AllIcons.Actions.Edit,
  ) {
    override fun update(e: AnActionEvent) {
      val interpreter = selectedInterpreter()
      e.presentation.isEnabledAndVisible =
        when (interpreter) {
          is TargetPythonInterpreter -> true
          is NativePythonInterpreter, null -> false
        }
    }

    override fun perform(interpreter: PythonInterpreter) = editInterpreter(interpreter)
  }

  /** Copies the path of the selected row. It also works for a broken SDK that has a path. */
  private inner class CopyInterpreterPathAction : DumbAwareAction(
    PyBundle.messagePointer("configurable.PyAllInterpretersConfigurable.row.action.copy.path"),
    Presentation.NULL_STRING,
    AllIcons.Actions.Copy,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      val path = selectedRow()?.path ?: return
      CopyPasteManager.getInstance().setContents(StringSelection(path))
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = selectedRow()?.path != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  /** Removes the SDK of [row]. It also works for a broken SDK, which the user can only remove or recreate. */
  private fun removeRow(row: PyInterpreterRow) {
    if (row.isInUse()) return
    PyPackageCoroutine.launch(project) {
      PythonInterpreterRegistry.getInstance(project).remove(row.entry)
      withContext(Dispatchers.EDT) {
        projectSdksModel.reset(project)
        reloadList()
      }
    }
  }

  private fun showPaths(interpreter: PythonInterpreter) {
    PyPackageCoroutine.launch(project) {
      interpreter.openPathsDialog(project) {
        markSdkModifiedFromRowAction()
        reloadList()
      }
    }
  }

  private fun editInterpreter(interpreter: PythonInterpreter) {
    val editor = interpreter.editorFor(project, this@PyAllInterpretersConfigurable) ?: return
    if (editor.showAndGet()) {
      markSdkModifiedFromRowAction()
      reloadList()
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

    override fun update(e: AnActionEvent) {
      super.update(e)
      // The interpreter library flow lists the interpreters of this project alone, so there is nothing to hide.
      e.presentation.isVisible = !isInterpreterLibraryFlowEnabled()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  companion object {
    const val ID: String = "com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyAllInterpretersConfigurable"

    private const val HINT_LINK_WORKSPACE = "workspace"
    private const val HINT_LINK_PACKAGES = "packages"
    private const val SEARCH_COLUMNS = 20
    private const val DEFAULT_VISIBLE_ROWS = 12
  }
}

/**
 * Renders an interpreter into [label] as the "All Interpreters" list row draws it:
 * name (or `[marker] name` when the interpreter is flagged) + optional `suffix` + description +
 * tooltip carrying the problem's reason. The icon is set by the caller so the label-text path
 * stays pure.
 */
internal fun renderPyInterpreterItemText(label: SimpleColoredComponent, item: PythonInterpreterPresentation) {
  val problem = item.problem
  if (problem != null) label.append("[${problem.marker}] ${item.name}", SimpleTextAttributes.ERROR_ATTRIBUTES)
  else label.append(item.name)
  item.suffix?.let { label.append(" $it") }
  label.append("  ${item.description}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
  label.toolTipText = problem?.reason
}

/**
 * The owner group title of the row at [index] of [list] when the row starts a group, else `null`. The rows come
 * grouped by owner, see [groupByOwner]. Without [SHOW_GROUPS] the list has no groups.
 */
private fun groupTitleAt(list: JList<out PyInterpreterRow>, index: Int): @Nls String? {
  if (list.getClientProperty(SHOW_GROUPS) != true) return null
  val model = list.model
  val title = model.getElementAt(index)?.groupTitle() ?: return null
  if (index == 0) return title
  return title.takeIf { model.getElementAt(index - 1)?.groupTitle() != it }
}

/** Whether [point] of [list] is on the group header above a row. */
private fun isOnGroupHeader(list: JList<out PyInterpreterRow>, point: Point): Boolean {
  val row = list.locationToIndex(point).takeIf { it >= 0 } ?: return false
  val bounds = list.getCellBounds(row, row) ?: return false
  return bounds.contains(point) && point.y < bounds.y + groupHeaderHeight(list, row)
}

/**
 * The client property of the list that says whether the list shows the owner groups: only a project with more than one
 * workspace has owners to tell apart.
 */
private const val SHOW_GROUPS = "PyAllInterpreters.showGroups"

/** The height of the group header that the row at [index] of [list] draws above itself, or 0 without one. */
private fun groupHeaderHeight(list: JList<out PyInterpreterRow>, index: Int): Int =
  if (groupTitleAt(list, index) != null) newGroupHeader().apply { caption = "X" }.preferredSize.height else 0

/** A group header without a line: the caption alone separates the groups. */
private fun newGroupHeader(): GroupHeaderSeparator =
  GroupHeaderSeparator(JBUI.CurrentTheme.Popup.separatorLabelInsets()).apply { setHideLine(true) }

/** "in use": small grey text for a row that a project uses, so it cannot be removed. */
private fun newInUseLabel(): JBLabel = JBLabel(PyBundle.message("python.interpreters.in.use")).apply {
  font = JBFont.small()
  foreground = NamedColorUtil.getInactiveTextColor()
}

/**
 * The [Sdk] behind [PythonInterpreter], for the redesigned page's per-row edit / remove / paths flows
 * that still bottom out in `Sdk`-typed APIs (`ProjectSdksModel`, `PyInterpreterEditDialogLauncher`, …).
 * The `getSdkAPI` bridge is deprecated by design; this single file-local accessor keeps the
 * suppression in one place instead of scattering it across every call site.
 */
private val PythonInterpreter.sdk: Sdk
  get() = getSdkAPI()

/** A row of the page: an entry of the registry, see [PythonInterpreterEntry]. */
internal sealed interface PyInterpreterRow {
  val entry: PythonInterpreterEntry

  /** The name that the row shows, for the search field. */
  val name: @NlsSafe String

  /** The path of the Python binary, for the search field and Copy Path, or `null` when the row has none. */
  val path: @NlsSafe String?

  /** What keeps the row selected across a reload, see [pickSelection]. */
  val selectionKey: PySdkName

  /** What the row draws. */
  fun presentation(): PythonInterpreterPresentation

  /**
   * A row of an interpreter. [inUse] when a project uses it, so it cannot be removed, see [isInUse]. [owner] is the name
   * of the Python project that owns it, see [ownerNameOf].
   */
  class Interpreter(
    override val entry: PythonInterpreterEntry.Available,
    val inUse: Boolean = false,
    val owner: @NlsSafe String? = null,
  ) : PyInterpreterRow {
    val interpreter: PythonInterpreter get() = entry.interpreter
    override val name: String get() = presentation().name
    override val path: String get() = interpreter.binaryPathString
    override val selectionKey: PySdkName get() = PySdkName("${interpreter.ownerDir}|${interpreter.ref}")
    override fun presentation(): PythonInterpreterPresentation = interpreter.presentation()
  }

  /** An entry that names no interpreter. [PythonInterpreterEntry.Unavailable.reason] says why. */
  class Broken(override val entry: PythonInterpreterEntry.Unavailable) : PyInterpreterRow {
    override val name: String get() = entry.name
    override val path: String? get() = entry.path
    override val selectionKey: PySdkName get() = PySdkName(entry.name)
    override fun presentation(): PythonInterpreterPresentation = entry.presentation
  }
}

/**
 * Returns the "Interpreter Settings" edit dialog for this interpreter, or `null` when it is a
 * legacy remote SDK that carries no target configuration. The Edit button skips the click on
 * `null`; whether the button is shown at all depends on the SDK being target-aware.
 */
internal fun PythonInterpreter.editorFor(project: Project, parent: Configurable): DialogWrapper? =
  PyInterpreterEditDialogLauncher.editorFor(project, this, parent)

/** Opens the interpreter-paths dialog for this interpreter and reports the mutated SDK. */
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
  rows: List<PyInterpreterRow>,
  hideOthers: Boolean,
  moduleBasePaths: Set<Path>,
): List<PyInterpreterRow> {
  if (!hideOthers) return rows
  return rows.filter { row ->
    val associated = when (row) {
      is PyInterpreterRow.Interpreter -> row.interpreter.associatedModuleDir?.toNioPath() ?: return@filter true
      // An entry that names no interpreter stays, so the user can remove it.
      is PyInterpreterRow.Broken -> return@filter true
    }
    associated in moduleBasePaths
  }
}

/** The identity of a row across a reload, see [PyInterpreterRow.selectionKey]. Typed, so the selection API and its test speak in the same currency, without a raw `String`. */
@JvmInline
internal value class PySdkName(val value: String)

/**
 * Chooses which SDK to keep selected after a reload. Prefers [previouslySelected]; falls back to
 * the first row. Strong-typed on [PySdkName] end-to-end so a rename or a stray String does not
 * silently slip through, and [PyAllInterpretersSelectionTest] pins every branch without a fixture.
 */
internal fun pickSelection(candidates: List<PySdkName>, previouslySelected: PySdkName?): PySdkName? =
  candidates.firstOrNull { it == previouslySelected } ?: candidates.firstOrNull()

/** Pure substring filter over the name and the path of a row. Case-insensitive; empty query keeps every row. */
internal fun filterBySearchQuery(rows: List<PyInterpreterRow>, query: String): List<PyInterpreterRow> {
  val q = query.trim().lowercase()
  if (q.isEmpty()) return rows
  return rows.filter { row ->
    row.name.lowercase().contains(q) || (row.path?.lowercase()?.contains(q) == true)
  }
}

/**
 * Renders each row as `<icon> <name> <version> <path>`, and "in use" on the trailing side for a row that a project uses.
 *
 * Selection paints as a rounded-corner rectangle (per PY-89840 design) via [SelectablePanel].
 */
private class PyInterpreterNameAndVersionRenderer : ListCellRenderer<PyInterpreterRow> {
  private val label = SimpleColoredComponent().apply { isOpaque = false }

  private val inUseLabel = newInUseLabel()

  private val inner: JPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
    add(label, BorderLayout.CENTER)
    add(inUseLabel, BorderLayout.EAST)
  }

  private val groupHeader: GroupHeaderSeparator = newGroupHeader()

  private val panel: SelectablePanel = SelectablePanel.wrap(inner).apply {
    val leftRightInset = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.unscaled.toInt()
    val innerInsets = JBUI.CurrentTheme.Popup.Selection.innerInsets().unscaled
    isOpaque = true
    selectionArc = JBUI.CurrentTheme.Popup.Selection.ARC.get()
    selectionInsets = JBInsets.create(0, leftRightInset)
    border = JBUI.Borders.empty(0, innerInsets.left + leftRightInset, 0, innerInsets.right + leftRightInset)
  }

  /** The row with an optional owner group header above it. Only the [panel] below the header paints the selection. */
  private val cell: JPanel = JPanel(BorderLayout()).apply {
    isOpaque = true
    add(groupHeader, BorderLayout.NORTH)
    add(panel, BorderLayout.CENTER)
  }

  override fun getListCellRendererComponent(
    list: JList<out PyInterpreterRow>?,
    value: PyInterpreterRow?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    label.clear()
    if (value != null) {
      // The SDK table, a broken SDK included: it has no ref, so it gets a presentation and no ref.
      val item = value.presentation()
      label.icon = item.icon.icon()
      renderPyInterpreterItemText(label, item)
    }
    inUseLabel.isVisible = value?.isInUse() == true
    val title = if (list != null && index >= 0) groupTitleAt(list, index) else null
    groupHeader.isVisible = title != null
    groupHeader.caption = title
    val bg = list?.background ?: UIUtil.getListBackground(false, false)
    panel.background = bg
    // The header paints the list background, so the selection of the row does not cover it.
    cell.background = bg
    val hovered = list != null && ListHoverListener.getHoveredIndex(list) == index
    panel.selectionColor = when {
      isSelected -> UIUtil.getListSelectionBackground(cellHasFocus)
      hovered -> JBUI.CurrentTheme.List.Hover.background(cellHasFocus)
      else -> null
    }
    return cell
  }
}

/**
 * The title of the owner group of this row: the name of the Python project that owns the interpreter. A broken SDK
 * names no interpreter, so it goes to the group of unavailable interpreters.
 */
private fun PyInterpreterRow.groupTitle(): @Nls String = when (this) {
  is PyInterpreterRow.Interpreter -> owner ?: PyBundle.message("python.interpreters.group.unavailable")
  is PyInterpreterRow.Broken -> PyBundle.message("python.interpreters.group.unavailable")
}

/**
 * [rows] grouped by owner, in the order of [ownerOrder], the names of the Python projects of the project. Owners that
 * are not in [ownerOrder] follow, and the unavailable interpreters come last. The order inside a group stays.
 */
internal fun groupByOwner(rows: List<PyInterpreterRow>, ownerOrder: List<String>): List<PyInterpreterRow> {
  fun rank(row: PyInterpreterRow): Int = when (row) {
    is PyInterpreterRow.Interpreter -> row.owner?.let { owner -> ownerOrder.indexOf(owner).takeIf { it >= 0 } } ?: ownerOrder.size
    is PyInterpreterRow.Broken -> ownerOrder.size + 1
  }
  return rows.groupBy { it.groupTitle() }.values.sortedBy { rank(it.first()) }.flatten()
}

/**
 * The name of the Python project of this snapshot that owns [interpreter]: its base dir is the owner dir of the
 * interpreter. Without such a project, the name of the owner dir.
 */
internal fun EvoPyProjectModel.Snapshot.ownerNameOf(interpreter: PythonInterpreter): @NlsSafe String =
  evoPyProjects.firstOrNull { it.pyProject.baseDir == interpreter.ownerDir }?.pyProject?.residesOnModule?.name
  ?: interpreter.ownerDir.fileName?.toString()
  ?: interpreter.ownerDir.toString()

/** Whether a project uses the interpreter of this row. A broken SDK names no interpreter, so no project uses it. */
private fun PyInterpreterRow.isInUse(): Boolean = when (this) {
  is PyInterpreterRow.Interpreter -> inUse
  is PyInterpreterRow.Broken -> false
}

/**
 * Whether a project of this snapshot uses [interpreter]: an interpreter library entry names it, or in the legacy flow a
 * module uses it. All Interpreters is a history of environments, so an interpreter in use cannot be removed there: a
 * module would keep naming an interpreter that is gone.
 */
internal fun EvoPyProjectModel.Snapshot.isInUse(interpreter: PythonInterpreter): Boolean =
  PythonInterpreterKey(interpreter.ownerDir, interpreter.ref) in interpreterKeys || interpreter in interpreters
