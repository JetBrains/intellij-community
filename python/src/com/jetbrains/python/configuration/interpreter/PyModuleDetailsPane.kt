// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration.interpreter

import com.intellij.ide.DataManager
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.ex.Settings
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.pyproject.model.usePyprojectToml
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET
import com.jetbrains.python.PyBundle
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.runWithSdkConfigurationLock
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import org.jetbrains.annotations.Nls

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

  private val sdkCombo: PyInterpreterComboBox =
    PyInterpreterComboBox(project, { pyProject.residesOnModule }, navigateToAllInterpreters = ::navigateToAllInterpreters)

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

  private val model: PyModuleDetailsModel<PythonInterpreter> = PyModuleDetailsModel(initialSdkName = null)

  val component: JComponent

  init {
    sdkCombo.addSelectionListener { refreshPackages() }
    component = buildLayout()
    sdkCombo.reload { moduleInterpreter ->
      model.markSdkApplied(moduleInterpreter)
      moduleInterpreter
    }
  }

  fun isModified(): Boolean {
    if (model.isSdkChanged(selectedInterpreter())) return true
    if (sourcesConfigurable.isModified()) return true
    return projectLinksPane.isModified()
  }

  fun reset() {
    sdkCombo.reload { it }
    sourcesConfigurable.reset()
    projectLinksPane.reset()
    refreshPackages()
  }

  /**
   * Reads the interpreter of the module again, and rebuilds only the SDK combo. For a change outside this page, such as
   * a pick in the widget. A selection that the user made here and has not applied stays when the combo still holds it.
   * Without such a selection the combo follows the module, so the change does not look like an edit of the user.
   */
  fun refreshModuleSdk() {
    sdkCombo.reload { moduleInterpreter ->
      val pendingSelection = selectedInterpreter().takeIf { model.isSdkChanged(it) }
      model.markSdkApplied(moduleInterpreter)
      pendingSelection ?: moduleInterpreter
    }
  }

  /** Rebuilds only the combo, for an interpreter that was added or removed outside this page. The selection stays. */
  fun refreshSdkCombo() {
    sdkCombo.reloadKeepingSelection()
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

  private fun refreshPackages() {
    packagesTree.setInterpreter(selectedInterpreter())
  }

  private fun selectedInterpreter(): PythonInterpreter? = sdkCombo.selectedInterpreter

  private fun buildLayout(): JComponent {
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
      label.labelFor = sdkCombo.labeledComponent
      add(label, c)
      c.gridx = 1; c.weightx = 1.0
      add(sdkCombo.component, c)
    }

    return JPanel(BorderLayout()).apply {
      add(header, BorderLayout.NORTH)
      add(tabs, BorderLayout.CENTER)
      minimumSize = Dimension(0, 0)
      preferredSize = Dimension(0, 0)
    }
  }

  private fun navigateToAllInterpreters(source: JComponent) {
    val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(source)) ?: return
    val target = settings.find(PyAllInterpretersConfigurable.ID) ?: return
    settings.select(target)
  }
}
