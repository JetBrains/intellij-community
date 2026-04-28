// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.mcpserver.McpManagedSessionSupport
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCategory
import com.intellij.mcpserver.McpToolsMarkdownExporter
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpToolDisallowListSettings.ToolState
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.builder.impl.CollapsibleTitledSeparatorImpl
import com.intellij.util.text.NameUtilCore
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.nio.file.Path
import javax.swing.AbstractAction
import javax.swing.DefaultListCellRenderer
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import kotlin.io.path.writeText

private const val EXPAND_LINK = "..."
private const val DESCRIPTION_COLLAPSED_MAX_LENGTH = 140
private const val DESCRIPTION_COLLAPSED_LINK_GAP = 4

internal data class ToolCategoryGroup(
  val category: McpToolCategory,
  val tools: List<McpTool>,
)

internal data class DescriptionRenderModel(
  val collapsedPreview: String,
  val collapsedTruncated: Boolean,
  val expandedRows: List<@NlsSafe String>,
)

internal fun buildCategoryGroups(tools: List<McpTool>): List<ToolCategoryGroup> {
  return tools
    .groupBy { it.descriptor.category }
    .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it.shortName })
    .map { (category, categoryTools) ->
      ToolCategoryGroup(
        category = category,
        tools = categoryTools.sortedBy { it.descriptor.name.lowercase() },
      )
    }
}

internal fun buildDescriptionRenderModel(
  description: String,
  collapsedTextWidth: Int,
  expandedTextWidth: Int,
  textWidth: (String) -> Int,
): DescriptionRenderModel {
  val expandedDescription = description.trimIndent().trim()
  val collapsedDescription = expandedDescription
    .lineSequence()
    .joinToString(" ") { it.trim() }
    .replace(Regex("\\s+"), " ")
    .trim()
  val collapsedCandidate = collapsedDescription.take(DESCRIPTION_COLLAPSED_MAX_LENGTH).trimEnd()
  val fullWidthPreview = truncateTextToWidth(collapsedCandidate, collapsedTextWidth, textWidth)
  val needsTruncation = fullWidthPreview.length < collapsedDescription.length
  val collapsedPreview = if (needsTruncation) {
    truncateTextToWidth(
      collapsedCandidate,
      (collapsedTextWidth - textWidth(EXPAND_LINK) - DESCRIPTION_COLLAPSED_LINK_GAP).coerceAtLeast(0),
      textWidth,
    )
  }
  else {
    fullWidthPreview
  }

  val expandedRows = if (expandedDescription.isEmpty()) {
    emptyList()
  }
  else {
    wrapTextIntoRows(expandedDescription, expandedTextWidth, textWidth)
  }

  return DescriptionRenderModel(
    collapsedPreview = collapsedPreview,
    collapsedTruncated = needsTruncation,
    expandedRows = expandedRows,
  )
}

internal fun truncateTextToWidth(
  text: String,
  maxWidth: Int,
  textWidth: (String) -> Int,
): String {
  if (text.isEmpty() || textWidth(text) <= maxWidth) return text

  var low = 0
  var high = text.length
  while (low < high) {
    val mid = (low + high + 1) / 2
    if (textWidth(text.substring(0, mid)) <= maxWidth) {
      low = mid
    }
    else {
      high = mid - 1
    }
  }
  return text.substring(0, low).trimEnd()
}

//This works better compared to using raw text in JBTextArea
private fun wrapTextIntoRows(text: String, availableWidth: Int, textWidth: (String) -> Int): List<String> {
  if (text.isBlank()) return listOf("")
  if (textWidth(text) <= availableWidth) return listOf(text)

  val rows = mutableListOf<String>()
  val words = text.split(Regex("\\s+"))
  var currentRow = ""
  for (word in words) {
    val candidate = if (currentRow.isEmpty()) word else "$currentRow $word"
    when {
      textWidth(candidate) <= availableWidth -> currentRow = candidate
      currentRow.isNotEmpty() && textWidth(word) <= availableWidth -> {
        rows += currentRow
        currentRow = word
      }
      else -> {
        if (currentRow.isNotEmpty()) rows += currentRow
        rows += splitLongWord(word, availableWidth, textWidth)
        currentRow = ""
      }
    }
  }
  if (currentRow.isNotEmpty()) {
    rows += currentRow
  }
  return rows
}

private fun splitLongWord(word: String, availableWidth: Int, textWidth: (String) -> Int): List<String> {
  if (word.isEmpty()) return emptyList()

  val result = mutableListOf<String>()
  var remaining = word
  while (remaining.isNotEmpty()) {
    val chunk = truncateTextToWidth(remaining, availableWidth, textWidth)
      .ifEmpty { remaining.first().toString() }
    result += chunk
    remaining = remaining.drop(chunk.length)
  }
  return result
}

/**
 * Configurable for managing MCP tool exposure in a grouped-list UI.
 */
class McpToolFilterConfigurable : SearchableConfigurable {
  // region Types

  private enum class ManagedSessionRouterMode(
    @param:Nls val displayName: String,
    val invocationMode: McpSessionInvocationMode,
  ) {
    ACP_ONLY(McpServerBundle.message("configurable.mcp.tool.filter.managed.router.acp.only"), McpSessionInvocationMode.DIRECT),
    ALL_AGENTS(McpServerBundle.message("configurable.mcp.tool.filter.managed.router.all.agents"), McpSessionInvocationMode.VIA_ROUTER),
    ;

    override fun toString(): String = displayName
  }

  // endregion

  // region Properties

  private val hasManagedSessionSupport: Boolean
    get() = McpManagedSessionSupport.isAvailable()

  private val showAdvancedFilterUi: Boolean
    get() = Registry.`is`("mcp.server.show.advanced.filter.options.ui", false)

  private var toolsContainerPanel: JPanel? = null
  private var searchField: SearchTextField? = null
  private val allToolStates = mutableMapOf<String, ToolState>()
  private var currentVisibleToolKeys: List<String> = emptyList()
  private var countLabel: JBLabel? = null
  private val categoryExpandedStates = mutableMapOf<String, Boolean>()
  private val descriptionExpandedStates = mutableMapOf<String, Boolean>()
  private var initialToolStates: Map<String, ToolState> = emptyMap()
  private var toolsFilterTextArea: JBTextArea? = null
  private var initialToolsFilter: @NlsSafe String = ""
  private var useUniversalToolRouterCheckBox: JCheckBox? = null
  private var managedSessionRouterModeComboBox: JComboBox<ManagedSessionRouterMode>? = null
  private var initialInvocationMode: McpSessionInvocationMode = McpSessionInvocationMode.DIRECT
  private var emptyStateLabel: JComponent? = null
  private var allTools: List<McpTool> = emptyList()
  private val searchableToolTexts = mutableMapOf<String, String>()
  private val categoryViews = mutableMapOf<String, CategoryView>()
  private val toolRowViews = mutableMapOf<String, ToolRowView>()
  private var updatingUiState = false

  // endregion

  // region Inner Types

  private data class CategoryView(
    val key: String,
    val group: ToolCategoryGroup,
    val panel: JPanel,
    val contentPanel: JPanel,
    val separator: CollapsibleTitledSeparatorImpl,
    val enabledCheckBox: ThreeStateCheckBox,
    val routerOnlyCheckBox: ThreeStateCheckBox,
    val toolRows: List<ToolRowView>,
  )

  private data class ToolRowView(
    val toolKey: String,
    val tool: McpTool,
    val panel: JPanel,
    val enabledCheckBox: JBCheckBox,
    val routerOnlyCheckBox: JBCheckBox,
  )

  // endregion

  // region SearchableConfigurable Overrides

  override fun getDisplayName(): String = McpServerBundle.message("configurable.mcp.tool.filter")

  override fun getId(): @NonNls String = "com.intellij.mcpserver.settings.filter"

  override fun createComponent(): JComponent {
    val panel = ScrollablePanel(BorderLayout())

    val settings = McpToolDisallowListSettings.getInstance()
    initialToolStates = settings.toolStates
    allToolStates.clear()
    allToolStates.putAll(initialToolStates)

    val filterSettings = McpToolFilterSettings.getInstance()
    initialInvocationMode = filterSettings.invocationMode
    initialToolsFilter = filterSettings.toolsFilter

    panel.add(createTopPanel(), BorderLayout.NORTH)

    val toolsPanel = NonOpaquePanel(VerticalLayout(0)).apply {
      border = JBUI.Borders.empty(8)
    }
    toolsContainerPanel = toolsPanel
    panel.add(toolsPanel, BorderLayout.CENTER)

    rebuildToolGroups()
    return panel
  }

  private val searchTextField = SearchTextField(false).apply {
    textEditor.emptyText.text = McpServerBundle.message("configurable.mcp.tool.filter.search.empty")
    textEditor.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        updateToolGroupsVisibility()
      }
    })
  }

  private fun createTopPanel(): JComponent = panel {
    if (hasManagedSessionSupport) {
      row(McpServerBundle.message("configurable.mcp.tool.filter.managed.router.label")) {
        val comboBox = comboBox(ManagedSessionRouterMode.entries)
          .onChanged {
            updateRouterOnlyControls()
          }
          .applyToComponent {
            selectedItem = currentManagedSessionRouterMode(initialInvocationMode)
            renderer = object : DefaultListCellRenderer() {
              override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
              ): Component {
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
                  text = (value as? ManagedSessionRouterMode)?.displayName
                }
              }
            }
          }
        managedSessionRouterModeComboBox = comboBox.component
      }.rowComment(McpServerBundle.message("configurable.mcp.tool.filter.router.comment"))
    }
    else {
      row {
        val checkbox = checkBox(McpServerBundle.message("configurable.mcp.tool.filter.router.checkbox"))
          .selected(initialInvocationMode == McpSessionInvocationMode.VIA_ROUTER)
          .onChanged {
            updateRouterOnlyControls()
          }
        useUniversalToolRouterCheckBox = checkbox.component
      }.rowComment(McpServerBundle.message("configurable.mcp.tool.filter.router.comment"))
    }

    row {
      cell(searchTextField)
        .align(AlignX.FILL)
    }

    if (showAdvancedFilterUi) {
      row {
        label(McpServerBundle.message("configurable.mcp.tool.filter.label"))
        button(McpServerBundle.message("dialog.mcp.tools.export.button")) {
          exportToMarkdown()
        }.align(AlignX.RIGHT)
      }
      row {
        val textArea = JBTextArea(initialToolsFilter)
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        toolsFilterTextArea = textArea
        scrollCell(textArea)
          .rows(2)
          .align(AlignX.FILL)
      }
      row {
        comment(McpServerBundle.message("configurable.mcp.tool.filter.example",
                                        "-*,+com.intellij.mcpserver.toolsets.general.*,-*.read_file"))
      }
    }
  }.also { searchField = searchTextField }

  // endregion

  // region State Management and UI Refresh

  private fun exportToMarkdown() {
    val descriptor = FileSaverDescriptor(
      McpServerBundle.message("dialog.mcp.tools.export.title"),
      McpServerBundle.message("dialog.mcp.tools.export.description"),
      "md",
    )
    val saveDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, null)
    val fileWrapper = saveDialog.save(null as Path?, "mcp_tools.md") ?: return

    val markdown = McpToolsMarkdownExporter.generateMarkdownForAllTools()
    val virtualFile = fileWrapper.getVirtualFile(true) ?: return
    virtualFile.toNioPath().writeText(markdown)
  }

  override fun isModified(): Boolean {
    val statesModified = statesToPersist() != normalizePersistedToolStates(initialToolStates)
    val invocationModeModified = currentInvocationMode() != initialInvocationMode
    val filterModified = showAdvancedFilterUi && (toolsFilterTextArea?.text ?: "") != initialToolsFilter

    return statesModified || invocationModeModified || filterModified
  }

  override fun apply() {
    val toolStates = statesToPersist()
    McpToolDisallowListSettings.getInstance().toolStates = toolStates
    initialToolStates = toolStates

    val filterSettings = McpToolFilterSettings.getInstance()
    filterSettings.invocationMode = currentInvocationMode()
    initialInvocationMode = filterSettings.invocationMode

    val filterChanged = if (showAdvancedFilterUi) {
      val newFilter = toolsFilterTextArea?.text ?: ""
      val changed = newFilter != initialToolsFilter
      filterSettings.toolsFilter = newFilter
      initialToolsFilter = newFilter
      changed
    }
    else {
      false
    }

    if (filterChanged) {
      rebuildToolGroups()
    }
  }

  override fun reset() {
    val settings = McpToolDisallowListSettings.getInstance()
    initialToolStates = settings.toolStates
    allToolStates.clear()
    allToolStates.putAll(initialToolStates)

    val filterSettings = McpToolFilterSettings.getInstance()
    initialInvocationMode = filterSettings.invocationMode
    useUniversalToolRouterCheckBox?.isSelected = initialInvocationMode == McpSessionInvocationMode.VIA_ROUTER
    managedSessionRouterModeComboBox?.selectedItem = currentManagedSessionRouterMode(initialInvocationMode)

    if (showAdvancedFilterUi) {
      initialToolsFilter = filterSettings.toolsFilter
      toolsFilterTextArea?.text = initialToolsFilter
    }

    rebuildToolGroups()
  }

  override fun disposeUIResources() {
    toolsContainerPanel = null
    searchField = null
    allToolStates.clear()
    currentVisibleToolKeys = emptyList()
    countLabel = null
    categoryExpandedStates.clear()
    descriptionExpandedStates.clear()
    toolsFilterTextArea = null
    useUniversalToolRouterCheckBox = null
    managedSessionRouterModeComboBox = null
    emptyStateLabel = null
    allTools = emptyList()
    searchableToolTexts.clear()
    categoryViews.clear()
    toolRowViews.clear()
  }

  // endregion

  private fun rebuildToolGroups() {
    val panel = toolsContainerPanel ?: return
    panel.removeAll()
    currentVisibleToolKeys = emptyList()
    countLabel = null
    categoryViews.clear()
    toolRowViews.clear()
    emptyStateLabel = null

    allTools = McpServerService.getInstance().getMcpToolsFiltered(
      useFiltersFromEP = false,
      excludeProviders = emptySet(),
    )
    initialToolStates = normalizeToolStateKeys(initialToolStates)
    val normalizedToolStates = normalizeToolStateKeys(allToolStates)
    allToolStates.clear()
    allToolStates.putAll(normalizedToolStates)
    searchableToolTexts.clear()
    searchableToolTexts.putAll(allTools.associate { tool ->
      toolKey(tool) to listOf(
        tool.descriptor.name,
        tool.descriptor.category.shortName,
        tool.descriptor.description,
        NameUtilCore.splitNameIntoWordList(tool.descriptor.category.shortName.removeSuffix("Toolset")).joinToString(" "),
      ).joinToString("\n").lowercase()
    })

    val countLabel = createCountLabel()
    this.countLabel = countLabel
    panel.add(createCountsPanel(countLabel))

    if (allTools.isEmpty()) {
      val emptyState = createEmptyState()
      emptyStateLabel = emptyState
      panel.add(emptyState)
    }
    else {
      buildCategoryGroups(allTools).forEach { group ->
        val categoryView = createCategoryComponent(group)
        panel.add(categoryView.panel)
      }
      val emptyState = createEmptyState().apply { isVisible = false }
      emptyStateLabel = emptyState
      panel.add(emptyState)
    }

    updateRouterOnlyControls()
    updateToolGroupsVisibility()
    panel.revalidate()
    panel.repaint()
  }

  private fun matchesSearchQuery(tool: McpTool): Boolean {
    val query = searchField?.text?.trim().orEmpty().lowercase()
    return query.isBlank() || searchableToolTexts[toolKey(tool)]?.contains(query) == true
  }

  private fun createCountsPanel(countLabel: JBLabel): JComponent {
    return NonOpaquePanel(HorizontalLayout(UIUtil.DEFAULT_HGAP)).apply {
      border = JBUI.Borders.emptyBottom(8)
      add(countLabel, HorizontalLayout.Group.RIGHT)
    }
  }

  private fun createCountLabel(): JBLabel {
    return JBLabel().apply {
      foreground = UIUtil.getContextHelpForeground()
      font = JBUI.Fonts.smallFont()
    }
  }

  private fun createToolRow(tool: McpTool, onCategoryStateChanged: () -> Unit): ToolRowView {
    val toolId = toolKey(tool)
    val state = currentToolState(tool)
    val toolNameLabel = JBLabel(tool.descriptor.name).apply {
      font = font.deriveFont(Font.BOLD)
    }
    val enabledCheckBox = JBCheckBox(McpServerBundle.message("mcp.tool.state.column.enabled"), state.enabled)
    val routerOnlyCheckBox = JBCheckBox(McpServerBundle.message("mcp.tool.state.column.on.demand"), state.routerOnly).apply {
      isEnabled = state.enabled
    }

    val controlsPanel = NonOpaquePanel(HorizontalLayout(UIUtil.DEFAULT_HGAP)).apply {
      add(enabledCheckBox)
      add(routerOnlyCheckBox)
    }
    val titleRow = NonOpaquePanel(HorizontalLayout(UIUtil.DEFAULT_HGAP)).apply {
      add(toolNameLabel)
      add(controlsPanel, HorizontalLayout.Group.RIGHT)
    }
    val descriptionRow = NonOpaquePanel(BorderLayout()).apply {
      add(ToolDescriptionPane(toolId, tool.descriptor.description.trimIndent()), BorderLayout.CENTER)
    }

    val panel = NonOpaquePanel(VerticalLayout(0)).apply {
      border = JBUI.Borders.empty(4, 0)
      add(titleRow)
      add(descriptionRow)
    }
    val toolRowView = ToolRowView(toolId, tool, panel, enabledCheckBox, routerOnlyCheckBox)
    enabledCheckBox.addActionListener {
      handleToolRowStateChange(toolId, toolRowView, onCategoryStateChanged) {
        it.copy(enabled = enabledCheckBox.isSelected)
      }
    }
    routerOnlyCheckBox.addActionListener {
      handleToolRowStateChange(toolId, toolRowView, onCategoryStateChanged) {
        it.copy(routerOnly = routerOnlyCheckBox.isSelected)
      }
    }
    return toolRowView
  }

  private fun toolKey(tool: McpTool): String = tool.descriptor.fullyQualifiedName

  private fun legacyToolKey(tool: McpTool): String = tool.descriptor.name

  private fun currentToolState(tool: McpTool): ToolState {
    return allToolStates[toolKey(tool)] ?: allToolStates[legacyToolKey(tool)] ?: ToolState()
  }

  private fun currentToolState(toolKey: String): ToolState = allToolStates[toolKey] ?: ToolState()

  private fun normalizeToolStateKeys(toolStates: Map<String, ToolState>): Map<String, ToolState> {
    if (toolStates.isEmpty() || allTools.isEmpty()) return toolStates

    val normalizedStates = toolStates.toMutableMap()
    val toolsByLegacyKey = allTools.groupBy(::legacyToolKey)
    allTools.forEach { tool ->
      val stableKey = toolKey(tool)
      if (normalizedStates.containsKey(stableKey)) return@forEach
      normalizedStates[legacyToolKey(tool)]?.let { normalizedStates[stableKey] = it }
    }
    toolsByLegacyKey.forEach { (legacyKey, toolsWithSameName) ->
      if (legacyKey != toolsWithSameName.firstOrNull()?.descriptor?.fullyQualifiedName &&
          toolsWithSameName.all { normalizedStates.containsKey(toolKey(it)) }) {
        normalizedStates.remove(legacyKey)
      }
    }
    return normalizedStates
  }

  private fun createCategoryComponent(group: ToolCategoryGroup): CategoryView {
    val categoryKey = group.category.fullyQualifiedName
    val categoryPanel = NonOpaquePanel(VerticalLayout(0))
    val contentPanel = NonOpaquePanel(VerticalLayout(0)).apply {
      border = JBUI.Borders.emptyLeft(JBUI.scale(20))
    }
    val enabledCheckBox = ThreeStateCheckBox(McpServerBundle.message("mcp.tool.state.column.enabled")).apply {
      isOpaque = false
    }
    val routerOnlyCheckBox = ThreeStateCheckBox(McpServerBundle.message("mcp.tool.state.column.on.demand")).apply {
      isOpaque = false
    }

    @Suppress("HardCodedStringLiteral")
    val readableTitle = NameUtilCore.splitNameIntoWordList(group.category.shortName.removeSuffix("Toolset")).joinToString(" ")
    val separator = CollapsibleTitledSeparatorImpl(readableTitle).apply {
      expanded = categoryExpandedStates.getOrDefault(categoryKey, true)
      setLabelFocusable(true)
      onAction { expanded ->
        categoryExpandedStates[categoryKey] = expanded
        updateCategoryVisibility(categoryKey)
      }
    }
    bindSpaceToAction(separator.label) {
      separator.expanded = !separator.expanded
    }
    lateinit var categoryView: CategoryView
    val toolRows = group.tools.map { tool ->
      createToolRow(tool) {
        refreshCategoryState(categoryView)
      }
    }

    enabledCheckBox.addActionListener {
      handleCategoryStateChange(categoryView) {
        val enabled = enabledCheckBox.state != ThreeStateCheckBox.State.NOT_SELECTED
        it.copy(enabled = enabled)
      }
    }
    routerOnlyCheckBox.addActionListener {
      val enabledTools = categoryView.group.tools.filter { currentToolState(it).enabled }
      handleCategoryStateChange(categoryView, enabledTools) {
        val routerOnly = routerOnlyCheckBox.state != ThreeStateCheckBox.State.NOT_SELECTED
        it.copy(routerOnly = routerOnly)
      }
    }

    val controlsPanel = NonOpaquePanel(HorizontalLayout(UIUtil.DEFAULT_HGAP)).apply {
      add(enabledCheckBox)
      add(routerOnlyCheckBox)
    }
    val headerPanel = NonOpaquePanel(BorderLayout(UIUtil.DEFAULT_HGAP, 0)).apply {
      add(separator, BorderLayout.CENTER)
      add(controlsPanel, BorderLayout.EAST)
    }

    toolRows.forEach { toolRow ->
      contentPanel.add(toolRow.panel)
    }
    categoryView = CategoryView(
      key = categoryKey,
      group = group,
      panel = categoryPanel,
      contentPanel = contentPanel,
      separator = separator,
      enabledCheckBox = enabledCheckBox,
      routerOnlyCheckBox = routerOnlyCheckBox,
      toolRows = toolRows,
    )
    categoryViews[categoryKey] = categoryView
    toolRows.forEach { toolRow ->
      toolRowViews[toolRow.toolKey] = toolRow
    }
    contentPanel.isVisible = separator.expanded
    refreshCategoryState(categoryView)

    categoryPanel.add(headerPanel)
    categoryPanel.add(contentPanel)
    return categoryView
  }

  private fun updateCategoryCheckBox(checkBox: ThreeStateCheckBox, state: ThreeStateCheckBox.State, isEnabled: Boolean = true) {
    checkBox.isThirdStateEnabled = state == ThreeStateCheckBox.State.DONT_CARE
    checkBox.state = state
    checkBox.isEnabled = isEnabled
  }

  private fun calculateCategoryEnabledState(tools: List<McpTool>): ThreeStateCheckBox.State {
    return toThreeStateCheckBoxState(commonState(tools) { currentToolState(it).enabled })
  }

  private fun calculateCategoryRouterOnlyState(tools: List<McpTool>): ThreeStateCheckBox.State {
    val enabledTools = tools.filter { currentToolState(it).enabled }
    if (enabledTools.isEmpty()) return ThreeStateCheckBox.State.NOT_SELECTED
    return toThreeStateCheckBoxState(commonState(enabledTools) { currentToolState(it).routerOnly })
  }

  private fun commonState(tools: List<McpTool>, valueProvider: (McpTool) -> Boolean): Boolean? {
    if (tools.isEmpty()) return null
    val firstValue = valueProvider(tools.first())
    return if (tools.all { valueProvider(it) == firstValue }) firstValue else null
  }

  private fun updateGroupToolStates(tools: List<McpTool>, stateUpdater: (ToolState) -> ToolState) {
    tools.forEach { tool ->
      updateToolState(toolKey(tool), stateUpdater)
    }
  }

  private fun updateToolState(toolName: String, stateUpdater: (ToolState) -> ToolState) {
    allToolStates[toolName] = stateUpdater(currentToolState(toolName))
  }

  private fun handleToolRowStateChange(
    toolName: String,
    toolRowView: ToolRowView,
    onCategoryStateChanged: () -> Unit,
    stateUpdater: (ToolState) -> ToolState,
  ) {
    if (updatingUiState) return
    updateToolState(toolName, stateUpdater)
    refreshToolRowState(toolRowView)
    onCategoryStateChanged()
    updateCountLabel()
  }

  private fun handleCategoryStateChange(
    categoryView: CategoryView,
    toolsToUpdate: List<McpTool> = categoryView.group.tools,
    stateUpdater: (ToolState) -> ToolState,
  ) {
    if (updatingUiState) return
    updateGroupToolStates(toolsToUpdate, stateUpdater)
    categoryView.toolRows.forEach(::refreshToolRowState)
    refreshCategoryState(categoryView)
    updateCountLabel()
  }

  private fun statesToPersist(): Map<String, ToolState> {
    val includeRouterOnly = isRouterOnlyAvailable()
    val initialRouterOnlyStates = normalizeToolStateKeys(initialToolStates).mapValues { (_, state) -> state.routerOnly }
    return normalizeToolStateKeys(allToolStates)
      .mapValues { (toolName, state) ->
        if (includeRouterOnly) state else state.copy(routerOnly = initialRouterOnlyStates[toolName] ?: ToolState().routerOnly)
      }
      .filterValues { it != ToolState() }
  }

  private fun normalizePersistedToolStates(toolStates: Map<String, ToolState>): Map<String, ToolState> {
    return normalizeToolStateKeys(toolStates).filterValues { it != ToolState() }
  }

  private fun toThreeStateCheckBoxState(value: Boolean?): ThreeStateCheckBox.State {
    return when (value) {
      true -> ThreeStateCheckBox.State.SELECTED
      false -> ThreeStateCheckBox.State.NOT_SELECTED
      null -> ThreeStateCheckBox.State.DONT_CARE
    }
  }

  private fun updateCountLabel() {
    val visibleToolKeys = currentVisibleToolKeys
    val enabledCount = visibleToolKeys.count { currentToolState(it).enabled }
    val routerOnlyCount = visibleToolKeys.count {
      val toolState = currentToolState(it)
      toolState.enabled && toolState.routerOnly
    }
    countLabel?.let { label ->
      label.text = McpServerBundle.message(
        "configurable.mcp.tool.filter.section.counts",
        enabledCount,
        routerOnlyCount,
      )
      label.repaint()
    }
  }

  private fun createEmptyState(): JComponent {
    return JBLabel(McpServerBundle.message("configurable.mcp.tool.filter.no.results")).apply {
      alignmentX = Component.LEFT_ALIGNMENT
      foreground = UIUtil.getContextHelpForeground()
      font = JBUI.Fonts.smallFont()
    }
  }

  private fun currentInvocationMode(): McpSessionInvocationMode {
    return if (hasManagedSessionSupport) {
      val selected = managedSessionRouterModeComboBox?.selectedItem as? ManagedSessionRouterMode
      selected?.invocationMode ?: initialInvocationMode
    }
    else if (useUniversalToolRouterCheckBox?.isSelected == true) {
      McpSessionInvocationMode.VIA_ROUTER
    }
    else {
      McpSessionInvocationMode.DIRECT
    }
  }

  private fun isRouterOnlyAvailable(): Boolean {
    return hasManagedSessionSupport || currentInvocationMode() == McpSessionInvocationMode.VIA_ROUTER
  }

  private fun updateToolGroupsVisibility() {
    val searchActive = searchField?.text?.isNotBlank() == true
    val visibleToolKeys = mutableListOf<String>()

    categoryViews.values.forEach { categoryView ->
      val visibleRows = categoryView.toolRows.filter { matchesSearchQuery(it.tool) }
      categoryView.toolRows.forEach { toolRow ->
        toolRow.panel.isVisible = toolRow in visibleRows
      }
      if (visibleRows.isNotEmpty()) {
        visibleToolKeys.addAll(visibleRows.map(ToolRowView::toolKey))
      }
      categoryView.panel.isVisible = visibleRows.isNotEmpty()
      categoryView.contentPanel.isVisible = visibleRows.isNotEmpty() && (searchActive || categoryView.separator.expanded)
    }
    currentVisibleToolKeys = visibleToolKeys
    updateCountLabel()

    emptyStateLabel?.isVisible = visibleToolKeys.isEmpty()
    toolsContainerPanel?.revalidate()
    toolsContainerPanel?.repaint()
  }

  private fun updateRouterOnlyControls() {
    var layoutChanged = false
    toolRowViews.values.forEach { toolRowView ->
      layoutChanged = refreshToolRowState(toolRowView) || layoutChanged
    }
    categoryViews.values.forEach { categoryView ->
      layoutChanged = refreshCategoryState(categoryView) || layoutChanged
    }
    updateCountLabel()
    if (layoutChanged) {
      toolsContainerPanel?.revalidate()
      toolsContainerPanel?.repaint()
    }
  }

  private fun refreshToolRowState(toolRowView: ToolRowView): Boolean {
    val state = currentToolState(toolRowView.tool)
    val showRouterOnlyControls = isRouterOnlyAvailable()
    var layoutChanged = false
    withUiStateUpdate {
      toolRowView.enabledCheckBox.isSelected = state.enabled
      toolRowView.routerOnlyCheckBox.isSelected = state.routerOnly
      toolRowView.routerOnlyCheckBox.isEnabled = state.enabled && showRouterOnlyControls
      layoutChanged = toolRowView.routerOnlyCheckBox.isVisible != showRouterOnlyControls
      toolRowView.routerOnlyCheckBox.isVisible = showRouterOnlyControls
    }
    toolRowView.panel.repaint()
    return layoutChanged
  }

  private fun refreshCategoryState(categoryView: CategoryView): Boolean {
    val showRouterOnlyControls = isRouterOnlyAvailable()
    var layoutChanged = false
    withUiStateUpdate {
      val enabledState = calculateCategoryEnabledState(categoryView.group.tools)
      updateCategoryCheckBox(categoryView.enabledCheckBox, enabledState)
      val routerOnlyEditable = showRouterOnlyControls && categoryView.group.tools.any { currentToolState(it).enabled }
      val routerOnlyState = calculateCategoryRouterOnlyState(categoryView.group.tools)
      updateCategoryCheckBox(categoryView.routerOnlyCheckBox, routerOnlyState, routerOnlyEditable)
      layoutChanged = categoryView.routerOnlyCheckBox.isVisible != showRouterOnlyControls
      categoryView.routerOnlyCheckBox.isVisible = showRouterOnlyControls
    }
    categoryView.panel.repaint()
    return layoutChanged
  }

  private fun updateCategoryVisibility(categoryKey: String) {
    val categoryView = categoryViews[categoryKey] ?: return
    val searchActive = searchField?.text?.isNotBlank() == true
    val hasVisibleRows = categoryView.toolRows.any { it.panel.isVisible }
    val newVisible = hasVisibleRows && (searchActive || categoryView.separator.expanded)
    val layoutChanged = categoryView.contentPanel.isVisible != newVisible
    categoryView.contentPanel.isVisible = newVisible
    if (layoutChanged) {
      categoryView.panel.revalidate()
      toolsContainerPanel?.revalidate()
    }
    categoryView.panel.repaint()
  }

  // region Helpers

  private fun currentManagedSessionRouterMode(invocationMode: McpSessionInvocationMode): ManagedSessionRouterMode =
    if (invocationMode == McpSessionInvocationMode.VIA_ROUTER) ManagedSessionRouterMode.ALL_AGENTS else ManagedSessionRouterMode.ACP_ONLY

  private inline fun withUiStateUpdate(action: () -> Unit) {
    updatingUiState = true
    try {
      action()
    }
    finally {
      updatingUiState = false
    }
  }

  private fun bindSpaceToAction(component: JComponent, action: () -> Unit) {
    val actionKey = "mcp.space.activate.${System.identityHashCode(component)}"
    component.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), actionKey)
    component.actionMap.put(actionKey, object : AbstractAction() {
      override fun actionPerformed(e: java.awt.event.ActionEvent?) {
        action()
      }
    })
  }

  // endregion

  // region ToolDescriptionPane

  private inner class ToolDescriptionPane(
    private val toolName: String,
    description: String,
  ) : JPanel() {
    private val rawDescription: @NlsSafe String = description
    private val descriptionFont = JBUI.Fonts.smallFont()
    private var viewport: JViewport? = null
    private var lastLayoutWidth = -1
    private var lastExpandedState = false
    private val layoutListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        rebuildIfNeeded()
      }
    }
    private val expandLink = ActionLink(EXPAND_LINK) {
      expandDescription()
    }.apply {
      autoHideOnDisable = false
      font = descriptionFont
    }
    private val collapseLink = ActionLink(McpServerBundle.message("configurable.mcp.tool.filter.description.collapse")) {
      collapseDescription()
    }.apply {
      autoHideOnDisable = false
      font = descriptionFont
    }

    init {
      layout = VerticalLayout(0)
      border = JBUI.Borders.emptyTop(UIUtil.DEFAULT_VGAP)
      isOpaque = false
      addComponentListener(layoutListener)
      rebuild()
      SwingUtilities.invokeLater { rebuildIfNeeded() }
    }

    private fun expandDescription() {
      descriptionExpandedStates[toolName] = true
      rebuild()
      SwingUtilities.invokeLater { collapseLink.requestFocus() }
    }

    private fun collapseDescription() {
      descriptionExpandedStates[toolName] = false
      rebuild()
      SwingUtilities.invokeLater { expandLink.requestFocus() }
    }

    override fun addNotify() {
      super.addNotify()
      parent?.addComponentListener(layoutListener)
      viewport = (SwingUtilities.getAncestorOfClass(JViewport::class.java, this) as? JViewport)
        .also { it?.addComponentListener(layoutListener) }
      SwingUtilities.invokeLater { rebuildIfNeeded() }
    }

    override fun removeNotify() {
      parent?.removeComponentListener(layoutListener)
      viewport?.removeComponentListener(layoutListener)
      viewport = null
      super.removeNotify()
    }

    private fun rebuildIfNeeded() {
      val layoutWidth = currentLayoutWidth()
      val expanded = isExpanded()
      if (layoutWidth > 0 && (layoutWidth != lastLayoutWidth || expanded != lastExpandedState)) {
        rebuild()
      }
    }

    private fun rebuild() {
      val layoutWidth = currentLayoutWidth()
      if (layoutWidth <= 0) return
      val expanded = isExpanded()
      lastLayoutWidth = layoutWidth
      lastExpandedState = expanded
      removeAll()
      val model = buildRenderModel(layoutWidth)
      if (model.collapsedPreview.isEmpty() && model.expandedRows.isEmpty()) {
        revalidateLayoutChain()
        return
      }

      if (expanded) {
        addExpandedDescription(model, layoutWidth)
      }
      else {
        addCollapsedDescription(model)
      }
      revalidateLayoutChain()
    }

    private fun addCollapsedDescription(model: DescriptionRenderModel) {
      add(NonOpaquePanel(HorizontalLayout(0)).apply {
        add(createDescriptionLabel(model.collapsedPreview))
        if (model.collapsedTruncated) {
          add(expandLink)
        }
      })
    }

    private fun addExpandedDescription(model: DescriptionRenderModel, layoutWidth: Int) {
      add(NonOpaquePanel(VerticalLayout(0)).apply {
        model.expandedRows.forEach { row ->
          add(createDescriptionLabel(row).apply {
            maximumSize = Dimension(layoutWidth, Int.MAX_VALUE)
          })
        }
      })
      add(collapseLink)
    }

    private fun createDescriptionLabel(text: @NlsSafe String): JBLabel {
      return JBLabel(text).apply {
        foreground = UIUtil.getContextHelpForeground()
        font = descriptionFont
      }
    }

    private fun isExpanded(): Boolean = descriptionExpandedStates[toolName] == true

    private fun buildRenderModel(layoutWidth: Int): DescriptionRenderModel {
      val linkWidth = expandLink.preferredSize.width
      val linkGap = JBUI.scale(DESCRIPTION_COLLAPSED_LINK_GAP)
      val minTextWidth = JBUI.scale(80)
      val collapsedTextWidth = (layoutWidth - linkWidth - linkGap).coerceAtLeast(minTextWidth)
      val expandedTextWidth = layoutWidth.coerceAtLeast(JBUI.scale(120))
      return buildDescriptionRenderModel(
        description = rawDescription,
        collapsedTextWidth = collapsedTextWidth,
        expandedTextWidth = expandedTextWidth,
      ) { getFontMetrics(descriptionFont).stringWidth(it) }
    }

    private fun currentLayoutWidth(): Int {
      val parentComponent = parent as? JComponent
      val candidates = buildList {
        val ownVisibleRectWidth = visibleRect.width
        if (ownVisibleRectWidth > 0) {
          add(ownVisibleRectWidth - insets.left - insets.right)
        }

        val viewportWidth = viewport?.extentSize?.width ?: 0
        if (viewportWidth > 0) {
          add(viewportWidth - insets.left - insets.right)
        }

        val parentWidth = parentComponent?.width ?: 0
        if (parentWidth > 0) {
          val parentInsets = parentComponent?.insets ?: JBUI.emptyInsets()
          add(parentWidth - parentInsets.left - parentInsets.right - insets.left - insets.right)
        }

        val parentVisibleRectWidth = parentComponent?.visibleRect?.width ?: 0
        if (parentVisibleRectWidth > 0) {
          add(parentVisibleRectWidth - insets.left - insets.right)
        }
        val ownWidth = width
        if (ownWidth > 0) {
          add(ownWidth - insets.left - insets.right)
        }
      }
      val widthCandidate = candidates.filter { it > 0 }.minOrNull() ?: return 0
      return widthCandidate.coerceAtLeast(JBUI.scale(120))
    }

    private fun revalidateLayoutChain() {
      revalidate()
      repaint()
      (parent?.parent as? JComponent)?.let {
        it.revalidate()
        it.repaint()
      }
      toolsContainerPanel?.let {
        it.revalidate()
        it.repaint()
      }
      viewport?.let {
        it.revalidate()
        it.repaint()
      }
    }
  }

  // endregion

}
