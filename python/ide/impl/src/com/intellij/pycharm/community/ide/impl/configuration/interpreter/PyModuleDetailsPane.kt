// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.python.sdk.backend.PythonSdkTable
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
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.python.pyproject.model.evolution.getInterpreter
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.pyproject.model.usePyprojectToml
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.pythonInterpreterResultAsync
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET
import com.jetbrains.python.PyBundle
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.collectAddInterpreterActions
import com.jetbrains.python.sdk.filter.filterAssignablePythonSdks
import com.jetbrains.python.sdk.interpreterItemsUnderProgress
import com.jetbrains.python.sdk.runWithSdkConfigurationLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import org.jetbrains.annotations.Nls

/**
 * One row of the interpreter combo. Typed so the renderer's `when` is exhaustive and the model
 * never carries an anonymous `Any?`.
 */
internal sealed interface SdkComboItem {
  /** Placeholder shown when the module has no matching Python SDK yet. */
  data object NoInterpreter : SdkComboItem

  /** A registered (editable clone of a) Python SDK. */
  data class Existing(val sdk: Sdk) : SdkComboItem

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
  private val projectSdksModel: ProjectSdksModel,
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
    propagate = { item -> sdkComboModel.setSelectedItemFromPresenter(item) },
  )

  /** Items above which the DSL renderer draws a `separator {}` — SDK-group boundaries + "All Interpreters…". */
  private val sdkComboSeparatorsAbove: MutableSet<SdkComboItem> = HashSet()

  /**
   * Cached `PyInterpreterItem` for each SDK the combo holds, computed once per rebuild in
   * [reloadSdkComboItems] via [interpreterItemsUnderProgress]. The renderer keys off `sdk.name`
   * (SDKs in the combo are editable copies whose identity survives every reload) so no cross-cell
   * suspend / read-action work is needed at paint time. Same building block the new interpreter
   * status-bar widget reads — see `PySdkStatusBar.getWidgetState`.
   */
  private var interpreterItemsBySdkName: Map<String, PyInterpreterItem> = emptyMap()

  private val sdkCombo: ComboBox<SdkComboItem> = ComboBox(sdkComboModel).apply {
    isSwingPopup = false
    preferredSize = preferredSize
    renderer = listCellRenderer<SdkComboItem> {
      // Swing's `ComboBoxWithWidePopup$AdjustingListCellRenderer` passes `null` for the
      // "editor" position when the combo has no selection yet — `LcrRow.value` is typed non-null
      // but the JVM does not enforce it, so the cast to nullable guards the `when` below from a
      // NoWhenBranchMatchedException at first paint.
      val v: SdkComboItem? = value
      if (v == null) return@listCellRenderer
      if (index >= 0 && v in sdkComboSeparatorsAbove) separator {}
      when (v) {
        SdkComboItem.NoInterpreter -> text(PyBundle.message("python.sdk.there.is.no.interpreter"))
        is SdkComboItem.Existing -> renderSdk(v.sdk)
        SdkComboItem.ShowAll -> text(PyBundle.message("configurable.PyWorkspaceStructureConfigurable.sdk.show.all"))
      }
    }
  }

  private fun com.intellij.ui.dsl.listCellRenderer.LcrRow<SdkComboItem>.renderSdk(sdk: Sdk) {
    val item = interpreterItemsBySdkName.getValue(sdk.name)
    icon(item.icon.icon())
    text(item.name)
    item.suffix?.let { s -> text(s) { foreground = greyForeground } }
    text(item.description) { foreground = greyForeground }
  }

  private val addInterpreterAction: DumbAwareAction = object : DumbAwareAction(
    PyBundle.messagePointer("configurable.PyWorkspaceStructureConfigurable.sdk.add"),
    Presentation.NULL_STRING,
    AllIcons.General.Add,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      val group = DefaultActionGroup().apply {
        addAll(collectAddInterpreterActions(moduleOrProject) { newSdk ->
          projectSdksModel.reset(project)
          reloadSdkComboItems()
          resetSdkComboSelection()
          findExistingByName(newSdk.name)?.let { sdkComboModel.selectedItem = it }
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

  /**
   * Snapshot of the module's Python SDK, populated by the init coroutine through the suspending
   * [findModuleSdk]. `null` until the first `refreshModuleSdkSnapshot` completes; UI reads
   * on the EDT, coroutine writes on the EDT after resolving.
   */
  private var moduleSdkSnapshot: Sdk? = null

  private val model: PyModuleDetailsModel = PyModuleDetailsModel(initialSdkName = null)

  val component: JComponent

  init {
    sdkCombo.addActionListener { refreshPackages() }
    component = buildLayout()
    moduleScope.launch {
      val sdk = findModuleSdk(pyProject)
      withContext(Dispatchers.EDT) {
        moduleSdkSnapshot = sdk
        model.markSdkApplied(sdk?.name)
        reloadSdkComboItems()
        resetSdkComboSelection()
        refreshPackages()
      }
    }
  }

  fun isModified(): Boolean {
    if (model.isSdkChanged(selectedExistingSdk()?.name)) return true
    if (sourcesConfigurable.isModified()) return true
    return projectLinksPane.isModified()
  }

  fun reset() {
    moduleScope.launch {
      val sdk = findModuleSdk(pyProject)
      withContext(Dispatchers.EDT) {
        moduleSdkSnapshot = sdk
        reloadSdkComboItems()
        resetSdkComboSelection()
        sourcesConfigurable.reset()
        projectLinksPane.reset()
        refreshPackages()
      }
    }
  }

  /** Rebuild only the SDK combo — for JDK-table add/remove/rename events fired outside this page. */
  fun refreshSdkCombo() {
    val previouslySelected = selectedExistingSdk()?.name
    reloadSdkComboItems()
    val match = previouslySelected?.let { findExistingByName(it) }
    if (match != null) sdkComboModel.selectedItem = match
    else resetSdkComboSelection()
  }

  /** Sync. Sources: modal progress. Links: runWriteAction. SDK: runWithSdkConfigurationLock → modal. */
  @Throws(ConfigurationException::class)
  fun apply() {
    val sourcesDirty = sourcesConfigurable.isModified()
    if (sourcesDirty) sourcesConfigurable.apply()

    val linksDirty = projectLinksPane.isModified()
    if (linksDirty) projectLinksPane.apply()

    val chosenSdk = selectedExistingSdk()
    val sdkChanged = model.isSdkChanged(chosenSdk?.name)
    if (sdkChanged) {
      val interpreter = resolveInterpreter(chosenSdk)
      applyInterpreterChange(interpreter)
      model.markSdkApplied(chosenSdk?.name)
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

  /** Resolve the combo's Sdk to a `PythonInterpreter`. Null `chosen` → null interpreter (clears SDK). */
  @Throws(ConfigurationException::class)
  private fun resolveInterpreter(chosen: Sdk?): PythonInterpreter? {
    if (chosen == null) return null
    val result = runWithModalProgressBlocking(project, PyBundle.message("configurable.PyWorkspaceStructureConfigurable.sdk.resolving")) {
      chosen.pythonInterpreterResultAsync()
    }
    return result.getOr { err -> throw ConfigurationException(err.error.message) }
  }

  @Throws(ConfigurationException::class)
  private fun applyInterpreterChange(interpreter: PythonInterpreter?) {
    // Settings applies on the EDT and does not read the snapshot back.
    val setResult = runWithSdkConfigurationLock(project) {
      pyProject.setPythonInterpreter(interpreter, waitForSnapshot = false)
    }
    setResult.getOr { err -> throw ConfigurationException(err.error.message) }
  }

  private fun reloadSdkComboItems() {
    val availableSdks = collectAvailablePythonSdks()
    val sdkByName = buildSdkLookup(availableSdks)
    // Cache items for every SDK that can end up in the combo, not just the filter-passed subset.
    // `buildSdkLookup` also carries the module's forced SDK (e.g. a WSL interpreter the association
    // filter stripped from `availableSdks`), and the renderer's `getValue(sdk.name)` would throw
    // `NoSuchElementException` on that name otherwise (WSL crash reported by Ilya).
    cacheInterpreterItems(sdkByName.values.toList())
    val items = buildComboItems(availableSdks, sdkByName)
    publishComboItems(items)
  }

  private fun collectAvailablePythonSdks(): List<Sdk> {
    val editable = PythonSdkTable.allSdks(project)
    return moduleOrProject.filterAssignablePythonSdks(editable)
  }

  /**
   * Flavor-aware `PyInterpreterItem`s built off the EDT via the same modal-progress helper the new
   * interpreter widget (`PySdkStatusBar`) and `PythonSdkComboBox` use. Cached by SDK name so the
   * cell renderer looks up flavor icon / suffix / description without hitting the file system per
   * paint. Order is preserved, so `zip` lines the two lists up 1:1.
   */
  private fun cacheInterpreterItems(sdks: List<Sdk>) {
    val items = sdks.interpreterItemsUnderProgress(project)
    interpreterItemsBySdkName = sdks.zip(items).associate { (sdk, item) -> sdk.name to item }
  }

  private fun buildComboItems(availableSdks: List<Sdk>, sdkByName: Map<String, Sdk>): List<SdkComboItem> {
    val contents = model.buildComboContents(
      pythonSdkNames = availableSdks.map { it.name },
      currentModuleSdkName = currentModuleSdk()?.name,
    )
    val items: MutableList<SdkComboItem> = contents.orderedSdkNames
      .mapNotNullTo(mutableListOf()) { name -> sdkByName[name]?.let { SdkComboItem.Existing(it) } }
    if (contents.includeNoInterpreter) items.add(SdkComboItem.NoInterpreter)
    items.add(SdkComboItem.ShowAll)
    return items
  }

  /**
   * Name → Sdk lookup that prefers the association-filtered [availableSdks] instance and falls
   * back to any editable clone in `ProjectSdksModel`. The module's forced SDK (uv-workspace case)
   * is not in [availableSdks] but the editable-clones map carries it, so the caller resolves the
   * row to the same instance the rest of the editor uses.
   */
  private fun buildSdkLookup(availableSdks: List<Sdk>): Map<String, Sdk> = buildMap {
    availableSdks.forEach { put(it.name, it) }
    PythonSdkTable.allSdks(project).forEach { putIfAbsent(it.name, it) }
  }

  private fun publishComboItems(items: List<SdkComboItem>) {
    sdkComboSeparatorsAbove.clear()
    sdkComboSeparatorsAbove.add(SdkComboItem.ShowAll)
    sdkComboModel.removeAll()
    sdkComboModel.add(items)
  }

  private fun resetSdkComboSelection() {
    val existing = currentModuleSdk()?.name?.let { findExistingByName(it) }
    sdkComboModel.selectedItem = existing ?: noInterpreterRowIfPresent()
  }

  /**
   * `SdkComboItem.NoInterpreter` is a `data object` — at most one instance ever appears in the
   * combo — so `contains` is enough. Returns `null` when the row is absent (i.e. real SDKs exist),
   * leaving [resetSdkComboSelection] to fall through to a null selection instead of forcing a
   * row the model does not carry.
   */
  private fun noInterpreterRowIfPresent(): SdkComboItem.NoInterpreter? =
    SdkComboItem.NoInterpreter.takeIf { it in sdkComboModel.items }

  private fun refreshPackages() {
    packagesTree.setSdk(selectedExistingSdk())
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
  private fun selectedExistingSdk(): Sdk? = when (val item = selectedComboItem) {
    is SdkComboItem.Existing -> item.sdk
    is SdkComboItem.NoInterpreter -> null
    is SdkComboItem.ShowAll -> null
    null -> null
  }

  /**
   * Returns the `Existing` row whose SDK name matches [name], or `null`. Uses an exhaustive `when`
   * so a new [SdkComboItem] variant forces a compile error here instead of a silent skip.
   */
  private fun findExistingByName(name: String): SdkComboItem.Existing? {
    for (row in sdkComboModel.items) {
      when (row) {
        is SdkComboItem.Existing -> if (row.sdk.name == name) return row
        SdkComboItem.NoInterpreter, SdkComboItem.ShowAll, null -> Unit
      }
    }
    return null
  }


  /**
   * Latest module SDK snapshot resolved by the init / reset coroutine through
   * [findModuleSdk]. `null` before the first resolve completes and after the module loses
   * its Python SDK — the pane treats both as "no interpreter".
   */
  private fun currentModuleSdk(): Sdk? = moduleSdkSnapshot

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

/** The SDK of the interpreter of [pyProject], for the combo that lists SDKs. */
private suspend fun findModuleSdk(pyProject: PyProject): Sdk? = pyProject.getInterpreter()?.getSdkAPI()

