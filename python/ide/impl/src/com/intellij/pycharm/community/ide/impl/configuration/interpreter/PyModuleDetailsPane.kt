// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW
import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.openapi.application.EDT
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.Disposable
import com.intellij.python.sdk.common.PyInterpreterItem
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.module.PyModuleService
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.collectAddInterpreterActions
import com.jetbrains.python.sdk.filterAssignablePythonSdks
import com.jetbrains.python.sdk.findPythonSdk
import com.jetbrains.python.sdk.interpreterItemsUnderProgress
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

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
 * `ModifiableRootModel` first, then SDK and dependencies open fresh models via
 * `ModuleRootModificationUtil.updateModel { }` — otherwise the sources cache would overwrite the
 * SDK / dep updates.
 */
internal class PyModuleDetailsPane(
  private val moduleOrProject: ModuleOrProject.ModuleAndProject,
  private val projectSdksModel: ProjectSdksModel,
  isMultiModuleProvider: () -> Boolean,
  parentDisposable: Disposable,
) {
  private val project = moduleOrProject.project
  private val module = moduleOrProject.module

  /**
   * Combo model that narrows the Swing `Any?` selection to [SdkComboItem]? and hands the routing
   * decision to [comboPresenter]. Keeping the routing in a Swing-free presenter makes it testable
   * by [com.intellij.python.junit5Tests.unit.SdkComboPresenterTest] — the pane only owns the three
   * side effects (hide popup, navigate, propagate).
   */
  private inner class SdkComboModel : CollectionComboBoxModel<SdkComboItem>() {
    override fun setSelectedItem(item: Any?) {
      if (item != null && item !is SdkComboItem) {
        super.setSelectedItem(item)
        return
      }
      comboPresenter.onSelectionChanged(item)
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
          projectSdksModel.addSdk(newSdk)
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

  private val packagesTree: PyPackagesTreePane = PyPackagesTreePane(moduleOrProject, parentDisposable)
  private val sourcesConfigurable: PySourcesConfigurable = PySourcesConfigurable(module)
  private val sourcesHolder: JPanel = JPanel(BorderLayout())
  private var sourcesBuilt: Boolean = false

  /**
   * Snapshot of the module's Python SDK, populated by the init coroutine through the suspending
   * [Module.findPythonSdk]. `null` until the first `refreshModuleSdkSnapshot` completes; UI reads
   * on the EDT, coroutine writes on the EDT after resolving.
   */
  private var moduleSdkSnapshot: Sdk? = null

  private val model: PyModuleDetailsModel = PyModuleDetailsModel(initialSdkName = null)
  private var initialInherited: Boolean =
    isMultiModuleProvider() && ModuleRootManager.getInstance(module).isSdkInherited

  val component: JComponent

  init {
    sdkCombo.addActionListener { refreshPackages() }
    component = buildLayout()
    PyPackageCoroutine.launch(project) {
      val sdk = module.findPythonSdk()
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
    return sourcesBuilt && sourcesConfigurable.isModified
  }

  fun reset() {
    PyPackageCoroutine.launch(project) {
      val sdk = module.findPythonSdk()
      withContext(Dispatchers.EDT) {
        moduleSdkSnapshot = sdk
        reloadSdkComboItems()
        resetSdkComboSelection()
        if (sourcesBuilt) sourcesConfigurable.reset()
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

  @Throws(ConfigurationException::class)
  fun apply() {
    val sourcesDirty = sourcesBuilt && sourcesConfigurable.isModified
    if (sourcesDirty) sourcesConfigurable.apply()

    val chosenSdk = selectedExistingSdk()
    val sdkChanged = model.isSdkChanged(chosenSdk?.name)
    if (sdkChanged) {
      applySdkChange(inherited = false, chosen = chosenSdk)
      initialInherited = false
      model.markSdkApplied(chosenSdk?.name)
    }

    if (sourcesBuilt && sdkChanged) sourcesConfigurable.reset()

    // Force-save project settings so `.iml` reflects the mutation on disk right after Apply, not after
    // the next unrelated auto-save. Without this, a user inspecting the `.iml` immediately after Apply
    // still sees the pre-Apply file (the model commit is in memory only until a save trigger fires).
    if (sourcesDirty || sdkChanged) {
      StoreUtil.saveDocumentsAndProjectSettings(project)
    }
  }

  fun dispose() {
    if (sourcesBuilt) sourcesConfigurable.disposeUIResources()
  }

  private fun ensureSourcesBuilt() {
    if (sourcesBuilt) return
    sourcesBuilt = true
    val sourcesComponent = sourcesConfigurable.createComponent() ?: return
    sourcesHolder.add(sourcesComponent, BorderLayout.CENTER)
    sourcesHolder.revalidate()
    sourcesHolder.repaint()
  }

  @OptIn(PyInternalExecApi::class)
  private fun applySdkChange(inherited: Boolean, chosen: Sdk?) {
    if (inherited) {
      ModuleRootModificationUtil.updateModel(module) { it.inheritSdk() }
    }
    else {
      WriteAction.run<RuntimeException> {
        PyModuleService.getInstance(project).setPythonSdk(module, chosen)
      }
    }
  }

  private fun reloadSdkComboItems() {
    val availableSdks = collectAvailablePythonSdks()
    cacheInterpreterItems(availableSdks)
    val items = buildComboItems(availableSdks)
    publishComboItems(items)
  }

  private fun collectAvailablePythonSdks(): List<Sdk> {
    val editable = projectSdksModel.projectSdks.values.toList()
    return project.filterAssignablePythonSdks(editable, module)
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

  private fun buildComboItems(availableSdks: List<Sdk>): List<SdkComboItem> {
    val contents = model.buildComboContents(
      pythonSdkNames = availableSdks.map { it.name },
      currentModuleSdkName = currentModuleSdk()?.name,
    )
    val sdkByName = buildSdkLookup(availableSdks)
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
    projectSdksModel.projectSdks.values.forEach { putIfAbsent(it.name, it) }
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
        SdkComboItem.NoInterpreter, SdkComboItem.ShowAll, -> Unit
      }
    }
    return null
  }


  /**
   * Latest module SDK snapshot resolved by the init / reset coroutine through
   * [Module.findPythonSdk]. `null` before the first resolve completes and after the module loses
   * its Python SDK — the pane treats both as "no interpreter".
   */
  private fun currentModuleSdk(): Sdk? = moduleSdkSnapshot

  private fun buildLayout(): JComponent {
    sdkCombo.minimumSize = Dimension(0, sdkCombo.preferredSize.height)
    sourcesHolder.minimumSize = Dimension(0, 0)
    sourcesHolder.preferredSize = Dimension(0, 0)

    val tabs = JBTabbedPane().apply {
      minimumSize = Dimension(0, 0)
      preferredSize = Dimension(0, 0)
      putClientProperty("JTabbedPane.hideContentBorder", true)
      tabComponentInsets = null
    }
    tabs.addTab(PyBundle.message("configurable.PyWorkspaceStructureConfigurable.tab.sources"), sourcesHolder)
    // "Packages" is labelled "Dependencies" — the packages tree already surfaces uv workspace
    // members and JPS module deps as pseudo-rows, so the standalone module-to-module Dependencies
    // tab is redundant. Keeping the module-dep list around for future reuse but out of the tab strip.
    tabs.addTab(PyBundle.message("configurable.PyWorkspaceStructureConfigurable.tab.dependencies"), packagesTree.component)
    ensureSourcesBuilt()

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
