// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.python.sdk.backend.PythonSdkTable
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.DialogPanel
import com.intellij.platform.util.coroutines.childScope
import com.intellij.python.pyproject.model.PyProjectModelSettings
import com.intellij.python.pyproject.model.usePyprojectToml
import com.intellij.ui.CollectionListModel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.asDisposable
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.module.PyModuleService
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

/**
 * "Python | Workspace Structure" (multi-module) / "Project Structure" (single module) settings page introduced by
 * the interpreter settings redesign (PY-89840).
 *
 * Layout mirrors the platform's Project Structure > Modules view: master list of Python modules on the left,
 * per-module editor on the right with SDK selector plus Packages / Sources / Dependencies tabs.
 */
internal class PyWorkspaceStructureConfigurable(private val project: Project) : SearchableConfigurable {

  // Cancelled in disposeUIResources. Child of project-level scope → survives dialog close, dies
  // with project. Primary lifecycle primitive: anything launched here stops silently on dispose.
  private val sessionScope: CoroutineScope =
    project.service<PyWorkspaceStructureScope>().scope.childScope("PyWorkspaceStructureConfigurable")

  // Bridge for legacy `parentDisposable` APIs (panes that register listeners via Disposer).
  // Cancelling [sessionScope] disposes this child via `asDisposable`.
  private val sessionDisposable: Disposable = sessionScope.asDisposable()

  private val projectSdksModel: ProjectSdksModel = ProjectSdksModel()

  /** [PyWorkspaceStructureModel]'s view of the editable-SDK pool — a thin adapter over [projectSdksModel]. */
  private val sdksTable: PyWorkspaceStructureModel.SdksTable = object : PyWorkspaceStructureModel.SdksTable {
    override fun trackedNames(): Set<SdkName> =
      PythonSdkTable.allSdks(project).mapTo(HashSet()) { SdkName(it.name) }

    override fun syncFromJdkTable() {
      // Re-read live JdkTable; add-time clone caused PY-92599.
      projectSdksModel.reset(project)
    }
  }
  private val model: PyWorkspaceStructureModel = PyWorkspaceStructureModel(
    sdksTable = sdksTable,
    onSdksChanged = { refreshAllModuleCombos() },
    // Platform fires JDK-table events from write actions; hop to EDT before touching the model
    // state so pool resync + combo refresh land after the write action releases (PY-92598).
    dispatcher = { task -> sessionScope.launch(Dispatchers.EDT) { task.run() } },
  )

  private val presenter: PyWorkspaceStructurePresenter = PyWorkspaceStructurePresenter()

  private val moduleListModel = CollectionListModel<Module>()
  private val moduleList: JBList<Module> = JBList(moduleListModel).apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    emptyText.text = PyBundle.message("configurable.PyWorkspaceStructureConfigurable.modules.empty")
    selectionBackground = background
    cellRenderer = ModuleListCellRenderer()
    addListSelectionListener {
      if (it.valueIsAdjusting) return@addListSelectionListener
      val selected = selectedValue
      if (selected != null) showModulePane(selected) else showPlaceholder()
    }
  }

  private val detailsCards: CardLayout = CardLayout()
  private val detailsHolder: JPanel = JPanel(detailsCards).apply {
    add(buildPlaceholder(), CARD_PLACEHOLDER)
    minimumSize = Dimension(0, 0)
    preferredSize = Dimension(0, 0)
  }

  private val modulePanes: MutableMap<Module, PyModuleDetailsPane> = LinkedHashMap()

  /** Owns the auto-configure checkbox. */
  private lateinit var dialogPanel: DialogPanel

  /** Toggle commits on Apply (not on click), so `apply()` can run the follow-up cleanup. */
  private lateinit var autoConfigCheckBox: JCheckBox

  /** Visible only on pending ON → OFF transition. */
  private lateinit var autoConfigWarning: JBLabel

  /** Content area wrapper. Swapped by [apply] when toml-off collapses to single-module. */
  private val contentHolder: JPanel = JPanel(BorderLayout())

  override fun getId(): String = ID

  override fun getDisplayName(): String =
    if (isMultiModule()) PyBundle.message("configurable.PyWorkspaceStructureConfigurable.workspace.display.name")
    else PyBundle.message("configurable.PyWorkspaceStructureConfigurable.project.display.name")

  override fun createComponent(): JComponent {
    projectSdksModel.reset(project)
    reloadModulesModel()

    // Subscription dies with the scope. Model is itself a `ProjectJdkTable.Listener`; its injected
    // EDT dispatcher defers each handler until the originating write action releases (PY-92598).
    ApplicationManager.getApplication().messageBus.connect(sessionScope).subscribe(
      ProjectJdkTable.JDK_TABLE_TOPIC,
      model,
    )

    rebuildContentHolder()
    val toggleVisible = isAutoConfigToggleVisible()
    dialogPanel = panel {
      if (toggleVisible) {
        row {
          checkBox(PyBundle.message("configurable.PyWorkspaceStructureConfigurable.auto.configure.pyproject.toml"))
            .applyToComponent {
              isSelected = project.usePyprojectToml
              autoConfigCheckBox = this
              addItemListener { updateAutoConfigWarningVisibility() }
            }
        }
        indent {
          row {
            autoConfigWarning = JBLabel(
              PyBundle.message("configurable.PyWorkspaceStructureConfigurable.auto.configure.pyproject.toml.warning"),
              AllIcons.General.Warning,
              SwingConstants.LEFT,
            ).apply {
              foreground = UIUtil.getContextHelpForeground()
              iconTextGap = JBUI.CurrentTheme.ActionsList.elementIconGap()
              isVisible = false
            }
            cell(autoConfigWarning)
          }.topGap(TopGap.NONE)
        }
      }
      row {
        cell(contentHolder).align(Align.FILL).resizableColumn()
      }.resizableRow()
    }.apply {
      val insets = UIUtil.PANEL_REGULAR_INSETS
      border = JBUI.Borders.empty(insets.top, insets.left, 0, insets.right)
    }
    updateAutoConfigWarningVisibility()
    return dialogPanel
  }

  /** Show "subprojects will be reset" only on pending ON → OFF. */
  private fun updateAutoConfigWarningVisibility() {
    if (!::autoConfigWarning.isInitialized || !::autoConfigCheckBox.isInitialized) return
    val live = project.usePyprojectToml
    autoConfigWarning.isVisible = live && !autoConfigCheckBox.isSelected
  }

  /** Rebuild split-vs-single layout after toml toggle. Tree label stays static (ConfigurableWrapper). */
  private fun swapContentLayout() {
    rebuildContentHolder()
    contentHolder.revalidate()
    contentHolder.repaint()
  }

  private fun rebuildContentHolder() {
    contentHolder.removeAll()
    contentHolder.add(if (isMultiModule()) buildSplitLayout() else buildSingleModuleLayout(), BorderLayout.CENTER)
  }

  override fun isModified(): Boolean {
    // Don't query `projectSdksModel.isModified` — this page doesn't apply/reset it, so flag never clears.
    if (isAutoConfigToggleVisible() &&
        ::autoConfigCheckBox.isInitialized &&
        autoConfigCheckBox.isSelected != project.usePyprojectToml) return true
    return modulePanes.values.any { it.isModified() }
  }

  /** Hide checkbox when registry is OFF (setter discards writes → isModified sticks). */
  private fun isAutoConfigToggleVisible(): Boolean =
    PyInterpreterSettingsFlags.isAutoConfigureToggleEnabled() &&
    PyProjectModelSettings.featureStateInRegistry != PyProjectModelSettings.FeatureState.OFF

  override fun reset() {
    projectSdksModel.reset(project)
    if (isAutoConfigToggleVisible() && ::autoConfigCheckBox.isInitialized) {
      autoConfigCheckBox.isSelected = project.usePyprojectToml
      updateAutoConfigWarningVisibility()
    }
    modulePanes.values.forEach { it.reset() }
    reloadModulesModel()
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    val toggleVisible = isAutoConfigToggleVisible()
    val wasEnabled = project.usePyprojectToml
    val nowEnabled = when {
      !toggleVisible -> wasEnabled
      ::autoConfigCheckBox.isInitialized -> autoConfigCheckBox.isSelected
      else -> wasEnabled
    }
    // Sync — pane.apply() commits via modal progress. Cleanup (if any) runs against settled .iml.
    for (pane in modulePanes.values) pane.apply()
    val outcome = presenter.decideToggleOutcome(wasEnabled, nowEnabled)
    presenter.commitToggle(project, wasEnabled, nowEnabled)
    updateAutoConfigWarningVisibility()
    when (outcome) {
      PyWorkspaceStructurePresenter.ToggleOutcome.ToggledOff -> {
        // Snapshot before clear — pipeline disposes these on sessionScope.
        val panesToDispose = modulePanes.values.toList()
        modulePanes.clear()
        detailsHolder.removeAll()
        detailsHolder.add(buildPlaceholder(), CARD_PLACEHOLDER)
        detailsCards.show(detailsHolder, CARD_PLACEHOLDER)
        moduleListModel.removeAll()
        // Null when the Configurable is shown outside Settings (search preview, standalone test
        // host) — the tree-label refresh is cosmetic there, so a missing host just skips it.
        val settingsHost = Settings.KEY.getData(DataManager.getInstance().getDataContext(dialogPanel))
        sessionScope.launch {
          presenter.runToggledOffPipeline(
            project = project,
            disposeUi = { panesToDispose.forEach { it.disposeAsync() } },
          )
          withContext(Dispatchers.EDT) {
            isMultiModuleCached = null
            swapContentLayout()
            reloadModulesModel()
            settingsHost?.revalidate()
          }
        }
      }
      PyWorkspaceStructurePresenter.ToggleOutcome.ToggledOn -> {
        modulePanes.values.forEach { it.refreshDependenciesEnabled() }
      }
      PyWorkspaceStructurePresenter.ToggleOutcome.NoChange -> Unit
    }
  }

  override fun disposeUIResources() {
    // Cancel first: subscriptions, cleanup continuations, pane coroutines all stop before we
    // touch Swing state. `sessionDisposable` is disposed by the scope's `invokeOnCompletion`.
    sessionScope.cancel()
    modulePanes.values.forEach { it.dispose() }
    modulePanes.clear()
    projectSdksModel.disposeUIResources()
  }

  /** Refresh SDK dropdown on JDK-table change from outside this page. */
  private fun refreshAllModuleCombos() {
    modulePanes.values.forEach { it.refreshSdkCombo() }
  }



  /** Memoized to keep `getDisplayName` and layout branches consistent for a single dialog session. */
  private var isMultiModuleCached: Boolean? = null

  private fun isMultiModule(): Boolean {
    isMultiModuleCached?.let { return it }
    val computed = computeIsMultiModule()
    isMultiModuleCached = computed
    return computed
  }

  private fun computeIsMultiModule(): Boolean = pythonRelevantModules().size > 1

  private fun pythonRelevantModules(): List<Module> {
    val service = PyModuleService.getInstance(project)
    val all = ModuleManager.getInstance(project).modules.toList()
    val filtered = all.filter { service.isPythonModule(it) }
    val visible = filtered.ifEmpty { all }
    return visible.sortedBy { it.name }
  }

  private fun reloadModulesModel() {
    val modules = pythonRelevantModules()
    moduleListModel.replaceAll(modules)
    if (modules.isNotEmpty()) {
      moduleList.setSelectedValue(modules.first(), true)
    }
    else {
      showPlaceholder()
    }
  }

  private fun buildSplitLayout(): JComponent {
    val listScroll = JBScrollPane(moduleList).apply {
      border = JBUI.Borders.empty()
      viewportBorder = JBUI.Borders.empty()
      minimumSize = Dimension(0, 0)
      preferredSize = Dimension(0, 0)
    }

    return OnePixelSplitter(false, SPLITTER_PROPORTION_KEY, DEFAULT_SPLITTER_PROPORTION).apply {
      setHonorComponentsMinimumSize(true)
      firstComponent = listScroll
      secondComponent = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.emptyLeft(UIUtil.PANEL_REGULAR_INSETS.left)
        add(detailsHolder, BorderLayout.CENTER)
        minimumSize = Dimension(0, 0)
        preferredSize = Dimension(0, 0)
      }
    }
  }

  private fun buildSingleModuleLayout(): JComponent {
    val module = pythonRelevantModules().firstOrNull()
    if (module != null) showModulePane(module)
    return detailsHolder
  }

  private fun showModulePane(module: Module) {
    val cardName = moduleCardKey(module)
    val existing = modulePanes[module]
    if (existing != null) {
      detailsCards.show(detailsHolder, cardName)
      return
    }
    // Pane needs a PyProject (not a bare Module); resolve off-EDT, then build + show.
    sessionScope.launch {
      val pyProject = module.asPyProject() ?: return@launch
      withContext(Dispatchers.EDT) {
        if (modulePanes[module] != null) return@withContext
        val pane = PyModuleDetailsPane(
          pyProject = pyProject,
          projectSdksModel = projectSdksModel,
          isMultiModuleProvider = ::isMultiModule,
          parentDisposable = sessionDisposable,
        )
        modulePanes[module] = pane
        detailsHolder.add(pane.component, cardName)
        if (moduleList.selectedValue == module || !isMultiModule()) {
          detailsCards.show(detailsHolder, cardName)
        }
      }
    }
    detailsCards.show(detailsHolder, CARD_PLACEHOLDER)
  }

  private fun showPlaceholder() {
    detailsCards.show(detailsHolder, CARD_PLACEHOLDER)
  }

  private fun moduleCardKey(module: Module): String = "module:${module.name}"

  private fun buildPlaceholder(): JComponent {
    return JLabel(PyBundle.message("configurable.PyWorkspaceStructureConfigurable.no.module"), SwingConstants.CENTER).apply {
      foreground = UIUtil.getInactiveTextColor()
    }
  }

  /**
   * Renders module rows with a rounded selection band via [SelectablePanel] — same recipe the
   * platform's popup list uses. A raw `SimpleListCellRenderer` would paint a full-width selection
   * rectangle, which spills past the arc corners; wrapping the label in a [SelectablePanel] with
   * `selectionArc` from the popup theme gives the Figma pill shape.
   */
  private class ModuleListCellRenderer : javax.swing.ListCellRenderer<Module> {
    private val label = SimpleColoredComponent().apply { isOpaque = false }
    private val panel: SelectablePanel = SelectablePanel.wrap(label).apply {
      val leftRightInset = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.unscaled.toInt()
      val innerInsets = JBUI.CurrentTheme.Popup.Selection.innerInsets().unscaled
      isOpaque = true
      selectionArc = JBUI.CurrentTheme.Popup.Selection.ARC.get()
      selectionInsets = JBInsets.create(0, leftRightInset)
      border = JBUI.Borders.empty(innerInsets.top, innerInsets.left + leftRightInset, innerInsets.bottom, innerInsets.right + leftRightInset)
    }

    override fun getListCellRendererComponent(
      list: javax.swing.JList<out Module>,
      value: Module?,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean,
    ): java.awt.Component {
      label.clear()
      label.icon = com.jetbrains.python.icons.PythonIcons.Python.PythonClosed
      if (value != null) label.append(value.name)
      val bg = list.background
      panel.background = bg
      panel.selectionColor = if (isSelected) UIUtil.getListSelectionBackground(cellHasFocus) else null
      return panel
    }
  }


  companion object {
    const val ID: String = "com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyWorkspaceStructureConfigurable"
    private const val CARD_PLACEHOLDER = "placeholder"
    // Master-list proportion key persists the divider position across reopens, per the platform
    // convention used by Project Structure / Manage Packages / VCS History.
    private const val SPLITTER_PROPORTION_KEY = "PyWorkspaceStructureConfigurable.splitter.proportion"
    private const val DEFAULT_SPLITTER_PROPORTION = 0.28f
  }
}

internal class PyWorkspaceStructureConfigurableProvider(private val project: Project) : ConfigurableProvider() {
  override fun canCreateConfigurable(): Boolean = PyInterpreterSettingsFlags.areNewInterpreterPagesEnabled()

  override fun createConfigurable(): Configurable = PyWorkspaceStructureConfigurable(project)
}

/** Project-scoped CoroutineScope for the Workspace Structure page. Dies with the project. */
@Service(Service.Level.PROJECT)
internal class PyWorkspaceStructureScope(val scope: CoroutineScope)
