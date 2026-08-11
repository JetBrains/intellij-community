// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.icons.AllIcons
import com.jetbrains.python.icons.PythonIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.util.text.trimMiddle
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.python.pyproject.PyDependencyGroup
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.sdk.findModuleForSdk
import com.jetbrains.python.sdk.pyInterpreterPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.BoxLayout
import javax.swing.Box
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class PyInstallDialogWorkspaceSelector(
  private val project: Project,
  private val packagingService: PyPackagingToolWindowService,
) {
  var selectedModule: PyWorkspaceMember? = null
    private set
  val selectedIJModule: Module? get() = selectedModule?.let { memberToModuleMap[it] }
  var selectedDependencyGroup: PyDependencyGroup = PyDependencyGroup("main")
    private set

  private var availableModules: List<PyWorkspaceMember> = emptyList()
  private var availableDependencyGroups: List<PyDependencyGroup> = emptyList()
  private var moduleGroups: Map<PyWorkspaceMember, List<PyDependencyGroup>> = emptyMap()
  private var memberToModuleMap: Map<PyWorkspaceMember, Module> = emptyMap()
  private var displayModuleName: String? = null
  private var pendingPreselectModule: String? = null
  private var pendingPreselectGroup: String? = null

  /** Preselected module/group applied after async workspace load completes. */
  fun preselect(moduleName: String?, groupName: String?) {
    pendingPreselectModule = moduleName
    pendingPreselectGroup = groupName
    if (availableModules.isNotEmpty()) applyPendingPreselect()
  }

  private fun applyPendingPreselect() {
    val moduleName = pendingPreselectModule
    val groupName = pendingPreselectGroup
    var changed = false
    if (moduleName != null) {
      availableModules.find { it.name == moduleName }?.let { picked ->
        if (selectedModule != picked) {
          selectedModule = picked
          displayModuleName = null
          updateGroupsForSelectedModule()
          changed = true
        }
      }
    }
    if (groupName != null) {
      availableDependencyGroups.find { it.name == groupName }?.let { picked ->
        if (selectedDependencyGroup != picked) {
          selectedDependencyGroup = picked
          changed = true
        }
      }
    }
    pendingPreselectModule = null
    pendingPreselectGroup = null
    if (changed) refreshSelectors()
  }

  private val rightPanel: JPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.CurrentTheme.ActionsList.elementIconGap(), 0)).apply { isOpaque = false }
  private var extraDocButton: JComponent? = null
  private var extraCloseButton: JComponent? = null

  init {
    loadWorkspaceMembers()
  }

  fun createTopPanel(docButton: JComponent? = null, closeButton: JComponent? = null): JComponent {
    extraDocButton = docButton
    extraCloseButton = closeButton
    refreshSelectors()
    val interpreter = createInterpreterLabel()
    val topPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      isOpaque = false
      if (interpreter != null) {
        interpreter.alignmentY = Component.CENTER_ALIGNMENT
        add(interpreter)
      }
      add(Box.createHorizontalGlue())
      rightPanel.alignmentY = Component.CENTER_ALIGNMENT
      add(rightPanel)
    }
    topPanel.addComponentListener(object : java.awt.event.ComponentAdapter() {
      override fun componentResized(e: java.awt.event.ComponentEvent) {
        fitTopPanel(topPanel, interpreter as? com.intellij.ui.SimpleColoredComponent)
      }
    })
    return topPanel
  }

  private fun fitTopPanel(topPanel: JComponent, interpreter: com.intellij.ui.SimpleColoredComponent?) {
    val totalWidth = topPanel.width
    if (totalWidth <= 0) return
    val safety = JBUI.scale(24)
    val docWidth = extraDocButton?.preferredSize?.width ?: 0
    val closeWidth = extraCloseButton?.preferredSize?.width ?: 0
    val avail = totalWidth - docWidth - closeWidth - safety
    if (avail <= 0) return
    // Split: interpreter ~50%, module ~25%, group ~25%.
    val interpBudget = (avail * 0.5).toInt()
    val moduleBudget = (avail * 0.25).toInt()
    val groupBudget = avail - interpBudget - moduleBudget
    if (interpreter != null) fitInterpreterToBudget(interpreter, interpBudget)
    fitDropdownToBudget(MODULE_DROPDOWN_KEY, moduleBudget)
    fitDropdownToBudget(GROUP_DROPDOWN_KEY, groupBudget)
    topPanel.revalidate()
    topPanel.repaint()
  }

  private fun createInterpreterLabel(): JComponent? {
    val sdk = packagingService.currentSdk ?: return null
    val presentation = sdk.pyInterpreterPresentation()
    val component = com.intellij.ui.SimpleColoredComponent().apply {
      icon = presentation.icon
      iconTextGap = JBUI.scale(4)
      isOpaque = false
      toolTipText = presentation.fullName
      putClientProperty(INTERPRETER_NAME_KEY, presentation.name)
      putClientProperty(INTERPRETER_SUFFIX_KEY, presentation.suffix)
    }
    fillInterpreterFragments(component, presentation.name, presentation.suffix)
    return component
  }

  private fun fillInterpreterFragments(component: com.intellij.ui.SimpleColoredComponent, name: String, suffix: String?) {
    val savedIcon = component.icon
    component.clear()
    component.icon = savedIcon
    component.append(name, com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES)
    if (!suffix.isNullOrBlank()) {
      val greyAttrs = com.intellij.ui.SimpleTextAttributes(
        com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN,
        UIUtil.getContextHelpForeground(),
      )
      component.append(" [$suffix]", greyAttrs)
    }
  }

  private fun fitInterpreterToBudget(interpreter: com.intellij.ui.SimpleColoredComponent, budget: Int) {
    val name = interpreter.getClientProperty(INTERPRETER_NAME_KEY) as? String ?: return
    val suffix = interpreter.getClientProperty(INTERPRETER_SUFFIX_KEY) as? String
    val iconArea = (interpreter.icon?.iconWidth ?: 0) + interpreter.iconTextGap
    val textBudget = budget - iconArea
    if (textBudget <= 0) return
    val fm = interpreter.getFontMetrics(interpreter.font)
    val suffixText = if (!suffix.isNullOrBlank()) " [$suffix]" else ""
    val suffixWidth = fm.stringWidth(suffixText)
    val nameBudget = textBudget - suffixWidth
    val shortName = trimToFit(name, nameBudget, fm)
    fillInterpreterFragments(interpreter, shortName, suffix)
    interpreter.revalidate()
    interpreter.repaint()
  }

  private fun fitDropdownToBudget(key: String, budget: Int) {
    val label = rightPanel.getClientProperty(key) as? JBLabel ?: return
    val raw = label.getClientProperty(RAW_TEXT_KEY) as? String ?: return
    val iconArea = (label.icon?.iconWidth ?: 0) + label.iconTextGap
    val borderInsets = label.border?.getBorderInsets(label)
    val borderH = (borderInsets?.left ?: 0) + (borderInsets?.right ?: 0)
    val arrowReserved = JBUI.scale(20)
    val textBudget = budget - iconArea - borderH - arrowReserved
    if (textBudget <= 0) return
    val fm = label.getFontMetrics(label.font)
    label.text = trimToFit(raw, textBudget, fm)
    label.revalidate()
    label.repaint()
  }

  private fun trimToFit(text: String, budget: Int, fm: java.awt.FontMetrics): String {
    if (budget <= 0 || text.isEmpty()) return text
    if (fm.stringWidth(text) <= budget) return text
    var lo = 4
    var hi = text.length
    while (lo < hi) {
      val mid = (lo + hi + 1) / 2
      if (fm.stringWidth(text.trimMiddle(mid)) <= budget) lo = mid else hi = mid - 1
    }
    return text.trimMiddle(lo)
  }


  private fun refreshSelectors() {
    rightPanel.removeAll()
    rightPanel.putClientProperty(MODULE_DROPDOWN_KEY, null)
    rightPanel.putClientProperty(GROUP_DROPDOWN_KEY, null)
    val moduleName = displayModuleName ?: selectedModule?.name
    if (moduleName != null) {
      val (moduleDropdown, moduleLabel) = createDropdownSelector(moduleName, PythonIcons.Python.PythonClosed, ::showModulePopup, tooltip = moduleName)
      moduleLabel.putClientProperty(RAW_TEXT_KEY, moduleName)
      rightPanel.add(moduleDropdown)
      rightPanel.putClientProperty(MODULE_DROPDOWN_KEY, moduleLabel)

      val groupName = selectedDependencyGroup.name
      val (groupDropdown, groupLabel) = createDropdownSelector(groupName, AllIcons.Toolwindows.ToolWindowHierarchy, ::showDependencyGroupPopup, tooltip = groupName)
      groupLabel.putClientProperty(RAW_TEXT_KEY, groupName)
      rightPanel.add(groupDropdown)
      rightPanel.putClientProperty(GROUP_DROPDOWN_KEY, groupLabel)
    }
    extraDocButton?.let { rightPanel.add(it) }
    extraCloseButton?.let { rightPanel.add(it) }
    rightPanel.revalidate()
    rightPanel.repaint()
  }

  private fun createDropdownSelector(
    @NlsContexts.Label text: String,
    icon: javax.swing.Icon,
    onClick: (JComponent) -> Unit,
    enabled: Boolean = true,
    @NlsContexts.Tooltip tooltip: String? = null,
  ): Pair<JComponent, JBLabel> {
    val vPad = JBUI.CurrentTheme.ActionsList.cellPadding().top
    val hGap = JBUI.CurrentTheme.ActionsList.elementIconGap()
    val label = JBLabel(text, icon, SwingConstants.LEFT).apply {
      border = JBUI.Borders.empty(vPad, hGap, vPad, hGap)
      isOpaque = false
      if (!enabled) foreground = UIUtil.getLabelDisabledForeground()
      if (tooltip != null) toolTipText = tooltip
    }
    val panel = JPanel(BorderLayout()).apply {
      isOpaque = false
      border = JBUI.Borders.empty(vPad)
      add(label, BorderLayout.CENTER)
      if (enabled) {
        add(JBLabel(AllIcons.General.ArrowDown).apply {
          border = JBUI.Borders.empty(vPad, 0, vPad, hGap)
        }, BorderLayout.EAST)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        val clickListener = object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent?) = onClick(this@apply)
        }
        addMouseListener(clickListener)
        for (child in components) {
          child.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
          child.addMouseListener(clickListener)
        }
      }
    }
    return panel to label
  }

  private fun showModulePopup(component: JComponent) {
    showSimpleChooserPopup(
      component,
      items = availableModules.ifEmpty { listOfNotNull(selectedModule) },
      namer = { it.name },
      renderCell = {
        icon(PythonIcons.Python.PythonClosed)
        text(value.name)
      },
    ) { chosen ->
      selectedModule = chosen
      displayModuleName = null
      updateGroupsForSelectedModule()
      refreshSelectors()
    }
  }

  private fun showDependencyGroupPopup(component: JComponent) {
    showSimpleChooserPopup(
      component,
      items = availableDependencyGroups.ifEmpty { listOf(selectedDependencyGroup) },
      namer = { it.name },
      renderCell = { text(value.name) },
    ) { chosen ->
      selectedDependencyGroup = chosen
      refreshSelectors()
    }
  }

  private fun <T : Any> showSimpleChooserPopup(
    anchor: JComponent,
    items: List<T>,
    namer: (T) -> String,
    renderCell: com.intellij.ui.dsl.listCellRenderer.LcrRow<T>.() -> Unit,
    onChosen: (T) -> Unit,
  ) {
    val builder = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(items)
      .setRenderer(listCellRenderer(renderCell))
      .setItemChosenCallback { onChosen(it) }
    if (items.size > FILTER_THRESHOLD) builder.setNamerForFiltering(namer)
    builder.createPopup().showUnderneathOf(anchor)
  }

  companion object {
    private const val FILTER_THRESHOLD = 8
    private const val INTERPRETER_NAME_KEY = "PyInstallDialog.interpreterName"
    private const val INTERPRETER_SUFFIX_KEY = "PyInstallDialog.interpreterSuffix"
    private const val MODULE_DROPDOWN_KEY = "PyInstallDialog.moduleDropdown"
    private const val GROUP_DROPDOWN_KEY = "PyInstallDialog.groupDropdown"
    private const val RAW_TEXT_KEY = "PyInstallDialog.rawText"
  }

  private fun loadWorkspaceMembers() {
    val sdk = packagingService.currentSdk ?: return
    val workspace = PythonPackageManager.forSdk(project, sdk).workspaceSupport
    packagingService.serviceScope.launch {
      val sdkModule = readAction { project.findModuleForSdk(sdk) }
      val state = PyInstallWorkspaceState.load(
        workspace = workspace,
        projectFallbackName = project.name,
        sdkModuleName = sdkModule?.name,
        sdkModule = sdkModule,
        preselectModuleName = pendingPreselectModule,
      )
      withContext(Dispatchers.EDT) {
        availableModules = state.availableModules
        moduleGroups = state.moduleGroups
        memberToModuleMap = state.memberToModuleMap
        displayModuleName = state.displayModuleName
        selectedModule = state.selectedModule
        updateGroupsForSelectedModule()
        refreshSelectors()
        applyPendingPreselect()
      }
    }
  }

  private fun updateGroupsForSelectedModule() {
    val member = selectedModule ?: return
    val groups = moduleGroups[member] ?: return
    availableDependencyGroups = groups
    if (selectedDependencyGroup !in availableDependencyGroups) {
      selectedDependencyGroup = availableDependencyGroups.firstOrNull() ?: return
    }
  }
}
