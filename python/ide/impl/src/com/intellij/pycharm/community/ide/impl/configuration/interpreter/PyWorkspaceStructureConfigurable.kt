// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.module.PyModuleService
import com.jetbrains.python.sdk.ModuleOrProject
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBInsets
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
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

  private val disposable: Disposable = Disposer.newDisposable(ID)
  private val projectSdksModel: ProjectSdksModel = ProjectSdksModel()

  /** [PyWorkspaceStructureModel]'s view of the editable-SDK pool — a thin adapter over [projectSdksModel]. */
  private val sdksTable: PyWorkspaceStructureModel.SdksTable = object : PyWorkspaceStructureModel.SdksTable {
    override fun trackedNames(): Set<String> =
      projectSdksModel.projectSdks.keys.mapTo(HashSet()) { it.name }

    override fun add(jdk: PyJdkHandle) {
      val original = jdk.originalSdk ?: return
      projectSdksModel.addSdk(original)
    }

    override fun removeByName(name: String) {
      val editable = projectSdksModel.projectSdks.entries.firstOrNull { it.key.name == name }?.value
      if (editable != null) projectSdksModel.removeSdk(editable)
    }
  }
  private val model: PyWorkspaceStructureModel = PyWorkspaceStructureModel(
    sdksTable = sdksTable,
    onSdksChanged = { refreshAllModuleCombos() },
  )

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

  override fun getId(): String = ID

  override fun getDisplayName(): String =
    if (isMultiModule()) PyBundle.message("configurable.PyWorkspaceStructureConfigurable.workspace.display.name")
    else PyBundle.message("configurable.PyWorkspaceStructureConfigurable.project.display.name")

  override fun createComponent(): JComponent {
    projectSdksModel.reset(project)
    reloadModulesModel()

    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(
      ProjectJdkTable.JDK_TABLE_TOPIC,
      object : ProjectJdkTable.Listener {
        override fun jdkAdded(jdk: Sdk) = model.onJdkAdded(PyJdkHandle.of(jdk))
        override fun jdkRemoved(jdk: Sdk) = model.onJdkRemoved(PyJdkHandle.of(jdk))
        override fun jdkNameChanged(jdk: Sdk, previousName: String) = model.onJdkRenamed()
      },
    )

    val content = if (isMultiModule()) buildSplitLayout() else buildSingleModuleLayout()
    return panel {
      row {
        cell(content).align(Align.FILL).resizableColumn()
      }.resizableRow()
    }.apply {
      val insets = UIUtil.PANEL_REGULAR_INSETS
      border = JBUI.Borders.empty(insets.top, insets.left, 0, insets.right)
    }
  }

  override fun isModified(): Boolean {
    if (projectSdksModel.isModified) return true
    return modulePanes.values.any { it.isModified() }
  }

  override fun reset() {
    projectSdksModel.reset(project)
    modulePanes.values.forEach { it.reset() }
    reloadModulesModel()
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    projectSdksModel.apply(null)
    for (pane in modulePanes.values) {
      pane.apply()
    }
  }

  override fun disposeUIResources() {
    projectSdksModel.disposeUIResources()
    modulePanes.values.forEach { it.dispose() }
    modulePanes.clear()
    Disposer.dispose(disposable)
  }

  /**
   * Refresh the SDK dropdown in every already-built [PyModuleDetailsPane] so a JDK-table change made
   * outside this page (e.g. via the "All Interpreters" sibling page) shows up without a Settings
   * reopen. The current selection is preserved when the module's effective SDK is still present.
   */
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
    if (existing == null) {
      val pane = PyModuleDetailsPane(
        moduleOrProject = ModuleOrProject.ModuleAndProject(module),
        projectSdksModel = projectSdksModel,
        isMultiModuleProvider = ::isMultiModule,
        parentDisposable = disposable,
      )
      modulePanes[module] = pane
      detailsHolder.add(pane.component, cardName)
    }
    detailsCards.show(detailsHolder, cardName)
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
  override fun canCreateConfigurable(): Boolean = PyInterpreterRedesignFlags.isEnabled()

  override fun createConfigurable(): Configurable = PyWorkspaceStructureConfigurable(project)
}
