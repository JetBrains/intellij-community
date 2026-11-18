// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.pyproject.model.usePyprojectToml
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.asItem
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.render.CompositeRenderer
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET
import com.jetbrains.python.PyBundle
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.collectAddInterpreterActions
import com.jetbrains.python.sdk.runWithSdkConfigurationLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import org.jetbrains.annotations.Nls

/**
 * One row of the interpreter combo. Typed so the renderer's `when` is exhaustive and the model
 * never carries an anonymous `Any?`.
 */
internal sealed interface SdkComboItem {
  /** Placeholder shown when the module has no interpreter to pick. */
  data object NoInterpreter : SdkComboItem

  /** An interpreter of the project. */
  data class Existing(val interpreter: PythonInterpreter) : SdkComboItem

  /** The caption of an owner group: the name of the Python project that owns the interpreters below. It is not selectable. */
  data class GroupHeader(val title: @NlsSafe String) : SdkComboItem

  /** Sentinel that shows or hides the interpreters of the other Python projects in the combo. */
  data object ToggleOtherProjects : SdkComboItem

  /** Sentinel that navigates to the "All Interpreters" settings page. */
  data object ShowAll : SdkComboItem
}

/**
 * Per-module editor: SDK combo + tabs (Sources / Dependencies / Packages).
 *
 * `apply()` order is deliberate: the Sources editor commits its long-lived cached
 * `ModifiableRootModel` first, then SDK and dependencies open fresh models — otherwise the sources
 * cache would overwrite the SDK / dep updates.
 */
internal class PyModuleDetailsPane(
  private val pyProject: PyProject,
  private val isMultiModuleProvider: () -> Boolean,
  initialProjectLinkCandidates: List<PyProject>,
  initialProjectLinks: Set<PyProject>,
  parentDisposable: Disposable,
) {
  private val project = pyProject.project
  private val moduleOrProject: ModuleOrProject.ModuleAndProject = ModuleOrProject.ModuleAndProject(pyProject)

  /** Scope dies with the module → every launch here no-ops silently on dispose. */
  private val moduleScope = PyModuleEditorScopes.of(pyProject)

  /**
   * Combo model that narrows the Swing `Any?` selection to [SdkComboItem]? and hands the routing
   * decision to [comboPresenter]. Keeping the routing in a Swing-free presenter makes it testable
   * by [com.intellij.python.junit5Tests.unit.SdkComboPresenterTest] — the pane only owns the three
   * side effects (hide popup, navigate, propagate).
   */
  private inner class SdkComboModel : CollectionComboBoxModel<SdkComboItem>() {
    // Combo is non-editable and holds only `SdkComboItem`, so any non-null selection is one of ours.
    override fun setSelectedItem(item: Any?) {
      comboPresenter.onSelectionChanged(item as SdkComboItem?)
    }

    /** Bypass path for [comboPresenter]'s `propagate` callback — writes straight to the base model. */
    fun setSelectedItemFromPresenter(item: SdkComboItem?) {
      super.setSelectedItem(item)
    }
  }

  private val sdkComboModel: SdkComboModel = SdkComboModel()

  private val comboPresenter: SdkComboPresenter = SdkComboPresenter(
    hidePopup = { sdkCombo.hidePopup() },
    navToAllInterpreters = { ApplicationManager.getApplication().invokeLater { navigateToAllInterpreters() } },
    toggleOtherProjects = { toggleOtherProjectInterpreters() },
    propagate = { item -> sdkComboModel.setSelectedItemFromPresenter(item) },
  )

  /** Items above which the DSL renderer draws a `separator {}`: "All interpreters…". */
  private val sdkComboSeparatorsAbove: MutableSet<SdkComboItem> = HashSet()

  /** The presentation of each interpreter the combo can hold, read off the EDT with [loadComboSource]. */
  private var interpreterItems: Map<PythonInterpreter, PyInterpreterItem> = emptyMap()

  private val sdkCombo: ComboBox<SdkComboItem> = ComboBox(sdkComboModel).apply {
    isSwingPopup = false
    preferredSize = preferredSize
    renderer = ComboRenderer(listCellRenderer<SdkComboItem> {
      // Swing's `ComboBoxWithWidePopup$AdjustingListCellRenderer` passes `null` for the
      // "editor" position when the combo has no selection yet — `LcrRow.value` is typed non-null
      // but the JVM does not enforce it, so the cast to nullable guards the `when` below from a
      // NoWhenBranchMatchedException at first paint.
      val v: SdkComboItem? = value
      if (v == null) return@listCellRenderer
      // The popup asks for the separator of a value with the index -1, so the separator follows the value.
      if (v in sdkComboSeparatorsAbove) separator {}
      when (v) {
        SdkComboItem.NoInterpreter -> text(PyBundle.message("python.sdk.there.is.no.interpreter"))
        is SdkComboItem.Existing -> renderInterpreter(v.interpreter)
        is SdkComboItem.GroupHeader -> text(v.title)
        SdkComboItem.ToggleOtherProjects -> {
          val key = if (showOtherProjects) "configurable.PyWorkspaceStructureConfigurable.sdk.hide.non.owned"
          else "configurable.PyWorkspaceStructureConfigurable.sdk.show.non.owned"
          text(PyBundle.message(key)) {
            foreground = greyForeground
            font = JBFont.small()
            align = LcrInitParams.Align.RIGHT
          }
        }
        SdkComboItem.ShowAll -> text(PyBundle.message("configurable.PyWorkspaceStructureConfigurable.sdk.show.all"))
      }
    })
  }

  private fun com.intellij.ui.dsl.listCellRenderer.LcrRow<SdkComboItem>.renderInterpreter(interpreter: PythonInterpreter) {
    val item = interpreterItems.getValue(interpreter)
    icon(item.icon.icon())
    text(item.name)
    item.suffix?.let { s -> text(s) }
    text(item.description) { foreground = greyForeground }
  }

  private val addInterpreterAction: DumbAwareAction = object : DumbAwareAction(
    PyBundle.messagePointer("configurable.PyWorkspaceStructureConfigurable.sdk.add"),
    Presentation.NULL_STRING,
    AllIcons.General.Add,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      val group = DefaultActionGroup().apply {
        addAll(collectAddInterpreterActions(ModuleOrProject.ModuleAndProject(comboSource.rootPyProject)) { interpreter ->
          reloadComboSource { findExisting(interpreter) ?: findExisting(selectedInterpreter()) }
        })
      }
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(
        null, group, e.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false,
      )
      val source = e.inputEvent?.component
      if (source != null) popup.showUnderneathOf(source) else popup.showInBestPositionFor(e.dataContext)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private val addInterpreterButton: JComponent by lazy {
    val toolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLBAR, DefaultActionGroup(addInterpreterAction), true)
    toolbar.targetComponent = sdkCombo
    toolbar.component.apply {
      isOpaque = false
      border = JBUI.Borders.empty()
    }
  }

  private val packagesTree: PyPackagesTreePane = PyPackagesTreePane(moduleOrProject, parentDisposable, isMultiModuleProvider)
  private val sourcesConfigurable: PySourcesConfigurable = PySourcesConfigurable(pyProject)
  private val sourcesHolder: JPanel = JPanel(BorderLayout())

  /**
   * Built with preloaded candidate PyProjects + live deps that the caller resolved off-EDT, so the
   * tab paints its final state immediately — no empty-then-populated flash — and the field stays
   * non-null for the whole editor lifetime.
   */
  private val projectLinksPane: PyModuleDependenciesPane =
    PyModuleDependenciesPane(pyProject, initialProjectLinkCandidates, initialProjectLinks)

  /**
   * All "which tabs are visible right now" rules live here. The two providers are polled lazily so
   * the editor sees the current auto-configure toggle state on every rebuild.
   */
  private val tabsModel: PyModuleTabsModel = PyModuleTabsModel(
    isMultiModule = isMultiModuleProvider,
    usePyprojectToml = { project.usePyprojectToml },
  )

  /**
   * Tab strip. Content is populated by [rebuildTabs] from [tabsModel] — both at [buildLayout] time
   * and whenever the auto-configure toggle flips via [refreshProjectLinksEnabled].
   */
  private val tabs: JBTabbedPane = JBTabbedPane().apply {
    minimumSize = Dimension(0, 0)
    preferredSize = Dimension(0, 0)
    putClientProperty("JTabbedPane.hideContentBorder", true)
    tabComponentInsets = null
  }

  /** What the combo lists, read off the EDT by [loadComboSource]. Empty until the first read completes. */
  private var comboSource: ComboSource = ComboSource(null, pyProject, emptyList(), emptyList())

  /** Whether the combo also lists the interpreters of the other Python projects, see [SdkComboItem.ToggleOtherProjects]. */
  private var showOtherProjects: Boolean = false

  private val model: PyModuleDetailsModel<PythonInterpreter> = PyModuleDetailsModel(initialSdkName = null)

  val component: JComponent

  init {
    sdkCombo.addActionListener { refreshPackages() }
    component = buildLayout()
    moduleScope.launch {
      val source = loadComboSource()
      withContext(Dispatchers.EDT) {
        model.markSdkApplied(source.moduleInterpreter)
        applyComboSource(source)
        resetSdkComboSelection()
        refreshPackages()
      }
    }
  }

  fun isModified(): Boolean {
    if (model.isSdkChanged(selectedInterpreter())) return true
    if (sourcesConfigurable.isModified()) return true
    return projectLinksPane.isModified()
  }

  fun reset() {
    moduleScope.launch {
      val source = loadComboSource()
      withContext(Dispatchers.EDT) {
        applyComboSource(source)
        resetSdkComboSelection()
        sourcesConfigurable.reset()
        projectLinksPane.reset()
        refreshPackages()
      }
    }
  }

  /**
   * Reads the interpreter of the module again, and rebuilds only the SDK combo. For a change outside this page, such as
   * a pick in the widget. A selection that the user made here and has not applied stays when the combo still holds it.
   * Without such a selection the combo follows the module, so the change does not look like an edit of the user.
   */
  fun refreshModuleSdk() {
    moduleScope.launch {
      val source = loadComboSource()
      withContext(Dispatchers.EDT) {
        val pendingSelection = selectedInterpreter().takeIf { model.isSdkChanged(it) }
        model.markSdkApplied(source.moduleInterpreter)
        applyComboSource(source)
        val match = pendingSelection?.let { findExisting(it) }
        if (match != null) sdkComboModel.selectedItem = match else resetSdkComboSelection()
      }
    }
  }

  /** Rebuilds only the combo, for an interpreter that was added or removed outside this page. The selection stays. */
  fun refreshSdkCombo() {
    reloadComboSource { findExisting(selectedInterpreter()) }
  }

  /** Sync. Sources: modal progress. Links: runWriteAction. SDK: runWithSdkConfigurationLock → modal. */
  @Throws(ConfigurationException::class)
  fun apply() {
    val sourcesDirty = sourcesConfigurable.isModified()
    if (sourcesDirty) sourcesConfigurable.apply()

    val linksDirty = projectLinksPane.isModified()
    if (linksDirty) projectLinksPane.apply()

    val chosen = selectedInterpreter()
    val sdkChanged = model.isSdkChanged(chosen)
    if (sdkChanged) {
      applyInterpreterChange(chosen)
      model.markSdkApplied(chosen)
    }

    if (sdkChanged) sourcesConfigurable.reset()
    if (sdkChanged) projectLinksPane.reset()

    if (sourcesDirty || linksDirty || sdkChanged) model.flushSettings(project)
  }

  fun dispose() {
    sourcesConfigurable.disposeUIResources()
  }

  /** Teardown from any dispatcher. */
  suspend fun disposeAsync() {
    sourcesConfigurable.disposeAsync()
  }

  /**
   * Called from [PyWorkspaceStructureConfigurable.apply] after the page-level auto-configure
   * checkbox is committed, so the per-module tab strip reflects the new mode without a Settings
   * reopen. Also refreshes the pane's editable state when the tab remains visible.
   */
  fun refreshProjectLinksEnabled() {
    rebuildTabs()
    projectLinksPane.refreshEnabled()
  }

  /**
   * Title + component for each tab the model can return. Keeping the lookup here (and not in
   * [PyModuleTabsModel]) is deliberate — the model stays Swing-free and testable; the pane owns
   * every Swing-touching detail. An exhaustive `when` keeps a new [PyModuleTab] variant from
   * compiling silently.
   */
  private val PyModuleTab.componentAndTitle: Pair<@Nls String, JComponent>
    get() = when (this) {
      PyModuleTab.Sources ->
        PyBundle.message("configurable.PyWorkspaceStructureConfigurable.tab.sources") to sourcesHolder
      PyModuleTab.Dependencies ->
        PyBundle.message("configurable.PyWorkspaceStructureConfigurable.tab.dependencies") to packagesTree.component
      PyModuleTab.ProjectLinks ->
        PyBundle.message("configurable.PyWorkspaceStructureConfigurable.tab.project.links") to projectLinksPane.component
    }

  /**
   * Rebuild the tab strip from [tabsModel]. Preserves the user-selected tab across the rebuild
   * when possible — only the Project Links tab can come and go, and switching to Sources is the
   * safe fallback when the previously selected tab is dropped. Idempotent — safe to call from
   * init, from the apply-side toggle, and from reset.
   */
  private fun rebuildTabs() {
    val visible = tabsModel.visibleTabs()
    val previouslySelectedTitle = tabs.selectedIndex.takeIf { it >= 0 }?.let { tabs.getTitleAt(it) }
    tabs.removeAll()
    for (tab in visible) {
      val (title, component) = tab.componentAndTitle
      tabs.addTab(title, component)
    }
    previouslySelectedTitle
      ?.let { tabs.indexOfTab(it) }
      ?.takeIf { it >= 0 }
      ?.let { tabs.selectedIndex = it }
  }

  @Throws(ConfigurationException::class)
  private fun applyInterpreterChange(interpreter: PythonInterpreter?) {
    // Settings applies on the EDT and does not read the snapshot back.
    val setResult = runWithSdkConfigurationLock(project) {
      pyProject.setPythonInterpreter(interpreter, waitForSnapshot = false)
    }
    setResult.getOr { err -> throw ConfigurationException(err.error.message) }
  }

  /** Reads the combo source again off the EDT, rebuilds the combo, and selects the row that [selection] picks. */
  private fun reloadComboSource(selection: () -> SdkComboItem.Existing?) {
    moduleScope.launch {
      val source = loadComboSource()
      withContext(Dispatchers.EDT) {
        applyComboSource(source)
        val match = selection()
        if (match != null) sdkComboModel.selectedItem = match else resetSdkComboSelection()
      }
    }
  }

  /**
   * Reads what the combo lists from one snapshot: the interpreters of the workspace root, which every member shares,
   * and the interpreters of every Python project, for [SdkComboItem.ToggleOtherProjects].
   */
  private suspend fun loadComboSource(): ComboSource {
    val snapshot = EvoPyProjectModel.getInstance(project).snapshot()
    val evoPyProject = snapshot.forPyProject(pyProject)
    val rootPyProject = evoPyProject?.let(snapshot::workspaceOf)?.pyProject ?: pyProject
    val registry = PythonInterpreterRegistry.getInstance(project)
    val rootInterpreters = registry.interpreters(rootPyProject)
    val projectInterpreters = buildList {
      for (other in snapshot.evoPyProjects.toList()) addAll(registry.interpreters(other.pyProject))
    }.distinct()
    val moduleInterpreter = evoPyProject?.interpreter
    val all = (rootInterpreters + projectInterpreters + listOfNotNull(moduleInterpreter)).distinct()
    val items = withContext(Dispatchers.IO) { all.associateWith { it.asItem() } }
    val owners = all.associateWith { snapshot.ownerNameOf(it) }
    return ComboSource(moduleInterpreter, rootPyProject, rootInterpreters, projectInterpreters, items, owners)
  }

  private fun applyComboSource(source: ComboSource) {
    comboSource = source
    interpreterItems = source.items
    reloadSdkComboItems(kept = selectedInterpreter()?.takeIf { it in source.items })
  }

  /** Rebuilds the rows from [comboSource]. The combo also keeps [kept], so a selection that the user made stays. */
  private fun reloadSdkComboItems(kept: PythonInterpreter? = null) {
    val source = comboSource
    val shown = if (showOtherProjects) source.rootInterpreters + (source.projectInterpreters - source.rootInterpreters.toSet())
    else source.rootInterpreters
    val listed = if (kept == null || kept in shown) shown else listOf(kept) + shown
    val contents = model.buildComboContents(pythonSdkNames = listed, currentModuleSdkName = source.moduleInterpreter)
    val grouped = groupByOwner(contents.orderedSdkNames, source)
    // Group captions only when the combo lists the interpreters of more than one owner.
    val withHeaders = grouped.mapNotNullTo(HashSet()) { source.owners[it] }.size > 1
    val items: MutableList<SdkComboItem> = mutableListOf()
    var lastOwner: String? = null
    for (interpreter in grouped) {
      val owner = source.owners[interpreter]
      if (withHeaders && owner != null && owner != lastOwner) items.add(SdkComboItem.GroupHeader(owner))
      lastOwner = owner
      items.add(SdkComboItem.Existing(interpreter))
    }
    if (contents.includeNoInterpreter) items.add(SdkComboItem.NoInterpreter)
    val hasOtherProjects = source.projectInterpreters.any { it !in source.rootInterpreters }
    if (hasOtherProjects) items.add(SdkComboItem.ToggleOtherProjects)
    items.add(SdkComboItem.ShowAll)
    publishComboItems(items)
  }

  /**
   * [interpreters] grouped by owner: the interpreters of the workspace root first, then the other owners in the order
   * they come. The order inside a group stays.
   */
  private fun groupByOwner(interpreters: List<PythonInterpreter>, source: ComboSource): List<PythonInterpreter> {
    val rootOwner = source.rootInterpreters.firstOrNull()?.let { source.owners[it] }
    val groups = interpreters.groupBy { source.owners[it] }
    return (groups[rootOwner].orEmpty()) + groups.filterKeys { it != rootOwner }.values.flatten()
  }

  /** Shows or hides the interpreters of the other Python projects, keeps the selection, and opens the popup again. */
  private fun toggleOtherProjectInterpreters() {
    val selected = selectedInterpreter()
    showOtherProjects = !showOtherProjects
    reloadSdkComboItems(kept = selected)
    val match = findExisting(selected)
    if (match != null) sdkComboModel.selectedItem = match else resetSdkComboSelection()
    ApplicationManager.getApplication().invokeLater { sdkCombo.showPopup() }
  }

  private fun publishComboItems(items: List<SdkComboItem>) {
    sdkComboSeparatorsAbove.clear()
    sdkComboSeparatorsAbove.add(SdkComboItem.ShowAll)
    sdkComboModel.removeAll()
    sdkComboModel.add(items)
  }

  private fun resetSdkComboSelection() {
    sdkComboModel.selectedItem = findExisting(comboSource.moduleInterpreter) ?: noInterpreterRowIfPresent()
  }

  /**
   * `SdkComboItem.NoInterpreter` is a `data object` — at most one instance ever appears in the
   * combo — so `contains` is enough. Returns `null` when the row is absent (i.e. interpreters exist),
   * leaving [resetSdkComboSelection] to fall through to a null selection instead of forcing a
   * row the model does not carry.
   */
  private fun noInterpreterRowIfPresent(): SdkComboItem.NoInterpreter? =
    SdkComboItem.NoInterpreter.takeIf { it in sdkComboModel.items }

  private fun refreshPackages() {
    packagesTree.setInterpreter(selectedInterpreter())
  }

  /**
   * `sdkCombo` is `ComboBox<SdkComboItem>`; the Swing API returns [Any] though, so narrow to the
   * sealed root through a `when` (no `as?` / `as` cast). A non-matching value is treated the same
   * as an empty combo — the pane does not care why the selection is missing.
   */
  private val selectedComboItem: SdkComboItem?
    get() = when (val raw = sdkCombo.selectedItem) {
      is SdkComboItem -> raw
      else -> null
    }

  /**
   * Exhaustive over the sealed [SdkComboItem] plus `null`, so a new variant triggers a compile
   * error here instead of a silent null.
   */
  private fun selectedInterpreter(): PythonInterpreter? = when (val item = selectedComboItem) {
    is SdkComboItem.Existing -> item.interpreter
    is SdkComboItem.NoInterpreter -> null
    is SdkComboItem.GroupHeader -> null
    is SdkComboItem.ToggleOtherProjects -> null
    is SdkComboItem.ShowAll -> null
    null -> null
  }

  /** The row of [interpreter], or `null` when the combo has none. */
  private fun findExisting(interpreter: PythonInterpreter?): SdkComboItem.Existing? {
    if (interpreter == null) return null
    for (row in sdkComboModel.items) {
      when (row) {
        is SdkComboItem.Existing -> if (row.interpreter == interpreter) return row
        is SdkComboItem.GroupHeader, SdkComboItem.NoInterpreter, SdkComboItem.ToggleOtherProjects, SdkComboItem.ShowAll, null -> Unit
      }
    }
    return null
  }

  private fun buildLayout(): JComponent {
    sdkCombo.minimumSize = Dimension(0, sdkCombo.preferredSize.height)
    sourcesHolder.minimumSize = Dimension(0, 0)
    sourcesHolder.preferredSize = Dimension(0, 0)

    sourcesHolder.add(sourcesConfigurable.createComponent(), BorderLayout.CENTER)
    rebuildTabs()

    val header = JPanel(GridBagLayout()).apply {
      border = JBUI.Borders.emptyBottom(LEFT_RIGHT_INSET.get())
      val c = GridBagConstraints()
      c.fill = GridBagConstraints.HORIZONTAL
      c.insets = JBUI.insets(BW.get())
      c.gridx = 0; c.gridy = 0; c.weightx = 0.0
      val label = JLabel(PyBundle.message("configurable.PyWorkspaceStructureConfigurable.sdk.label"))
      label.labelFor = sdkCombo
      add(label, c)
      c.gridx = 1; c.weightx = 1.0
      add(sdkCombo, c)
      c.gridx = 2; c.weightx = 0.0; c.insets = JBUI.insets(BW.get(), 0, BW.get(), BW.get())
      add(addInterpreterButton, c)
    }

    return JPanel(BorderLayout()).apply {
      add(header, BorderLayout.NORTH)
      add(tabs, BorderLayout.CENTER)
      minimumSize = Dimension(0, 0)
      preferredSize = Dimension(0, 0)
    }
  }

  private fun navigateToAllInterpreters() {
    val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(sdkCombo)) ?: return
    val target = settings.find(PyAllInterpretersConfigurable.ID) ?: return
    settings.select(target)
  }
}

/** What the combo of a [PyModuleDetailsPane] lists. */
private class ComboSource(
  /** The interpreter the module uses, or `null` when it has none. */
  val moduleInterpreter: PythonInterpreter?,
  /** The root of the workspace of the module. It owns the interpreters the module can share. */
  val rootPyProject: PyProject,
  /** The interpreters of [rootPyProject]. The combo lists them by default. */
  val rootInterpreters: List<PythonInterpreter>,
  /** The interpreters of every Python project, for [SdkComboItem.ToggleOtherProjects]. */
  val projectInterpreters: List<PythonInterpreter>,
  /** The presentation of each interpreter above. */
  val items: Map<PythonInterpreter, PyInterpreterItem> = emptyMap(),
  /** The name of the Python project that owns each interpreter above, for the group headers of the combo. */
  val owners: Map<PythonInterpreter, String> = emptyMap(),
)

/**
 * The combo renderer: [rows] draws every row except an owner caption, which [GroupHeaderRow] draws. The popup does not
 * select a caption, see [ComboBox.SelectableItem].
 */
private class ComboRenderer(private val rows: ListCellRenderer<SdkComboItem>) : CompositeRenderer<SdkComboItem>() {
  private val header = GroupHeaderRow()
  private val headers = ListCellRenderer<SdkComboItem> { _, value, _, _, _ ->
    header.apply {
      caption.caption = when (value) {
        is SdkComboItem.GroupHeader -> value.title
        is SdkComboItem.Existing, SdkComboItem.NoInterpreter, SdkComboItem.ToggleOtherProjects, SdkComboItem.ShowAll, null -> null
      }
    }
  }

  override fun selectRenderer(value: SdkComboItem?): ListCellRenderer<*> = when (value) {
    is SdkComboItem.GroupHeader -> headers
    is SdkComboItem.Existing, SdkComboItem.NoInterpreter, SdkComboItem.ToggleOtherProjects, SdkComboItem.ShowAll, null -> rows
  }
}

/**
 * An owner caption of the combo popup: a group caption without a line. The popup cannot select it.
 *
 * The popup already indents each row, so the caption has no left inset of its own: it starts where the icons of the
 * rows start, as on the All Interpreters page.
 */
private class GroupHeaderRow : JPanel(BorderLayout()), ComboBox.SelectableItem {
  val caption: GroupHeaderSeparator = GroupHeaderSeparator(captionInsets()).apply { setHideLine(true) }

  init {
    isOpaque = false
    add(caption, BorderLayout.CENTER)
  }

  override fun isSelectable(): Boolean = false
}

/** The insets of a popup group caption, without the left inset. */
private fun captionInsets(): Insets {
  val insets = JBUI.CurrentTheme.Popup.separatorLabelInsets()
  // The values are scaled already, so they go into plain insets and are not scaled again.
  return Insets(insets.top, 0, insets.bottom, insets.right)
}
