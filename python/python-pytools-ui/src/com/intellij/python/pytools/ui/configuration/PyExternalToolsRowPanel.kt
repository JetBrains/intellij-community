// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.configuration

import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.OnOffButton
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * One expandable tool row on the External Tools page. Collapsed it shows the header (chevron, icon,
 * name, feature/status summary, informational lookup chain, on/off toggle); expanded it reveals the
 * tool's inline settings — the feature toggles (the tool's own [com.intellij.openapi.options.UnnamedConfigurable]
 * embedded inline), the per-SDK detection list with per-SDK `Install` links, and the executable Path
 * row with install / upgrade / revert / browse actions.
 *
 * All edits mutate the row's [RowState] / the embedded configurable and are committed on the page's
 * shared Apply — there is no per-tool modal dialog. The owning [PyExternalToolsList] (as [RowHost])
 * supplies project, the lookup-chain text, and the path/SDK actions.
 */
internal class PyExternalToolRowPanel(
  private val row: ToolRow,
  private val host: RowHost,
) : JPanel(BorderLayout()) {

  private val project get() = host.project
  private val tool get() = row.tool

  private var expanded = false
  private var detailBuilt = false

  /** The embedded feature-settings component once built; its live checkbox state feeds the header summary. */
  private var detailComponent: JComponent? = null

  private val chevron = JLabel(AllIcons.General.ChevronRight).apply {
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
  }
  private val iconLabel = JLabel(tool.icon)
  private val nameLabel = JBLabel(tool.presentableName).apply {
    font = JBFont.label().asBold()
    // Show the tool's one-line description on hover of its name.
    setToolTipText(HtmlChunk.text(tool.description))
  }
  private val summaryLabel = JBLabel()
  private val chainLabel = JBLabel()
  private val toggle = OnOffButton().apply {
    addActionListener {
      row.staged = row.staged.copy(enabled = isSelected)
      updateHeader()
    }
  }

  private val detailHolder = JPanel(BorderLayout())
  private val sdkSectionHolder = JPanel(BorderLayout())
  private val pathSectionHolder = JPanel(BorderLayout())
  private val body = JPanel(VerticalLayout(JBUI.scale(6))).apply {
    border = JBUI.Borders.empty(0, JBUI.scale(28), JBUI.scale(6), JBUI.scale(8))
    isVisible = false
    add(detailHolder)
    add(sdkSectionHolder)
    add(pathSectionHolder)
  }

  init {
    add(buildHeader(), BorderLayout.NORTH)
    add(body, BorderLayout.CENTER)
    applyNormalBorder()
    updateHeader()
  }

  private fun buildHeader(): JComponent {
    // Left cluster grows to fill; a trailing glue keeps its contents left-packed.
    val leftCluster = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      isOpaque = false
      add(chevron)
      add(Box.createHorizontalStrut(JBUI.scale(4)))
      add(iconLabel)
      add(Box.createHorizontalStrut(JBUI.scale(6)))
      add(nameLabel)
      add(Box.createHorizontalStrut(JBUI.scale(10)))
      add(summaryLabel)
      add(Box.createHorizontalGlue())
    }
    // Right side, mirroring the header bar: the chain sits at its natural width immediately left of
    // the fixed toggle column, so its right edge is a constant small gap from the toggle regardless
    // of the chain's length (with or without the "(m/n)" env count).
    val rightCluster = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      isOpaque = false
      add(chainLabel)
      add(Box.createHorizontalStrut(columnGap()))
      add(fixedWidthPanel(toggleColumnWidth(), toggle, BorderLayout.EAST))
    }
    val header = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(6, 8)
      isOpaque = false
      add(leftCluster, BorderLayout.CENTER)
      add(rightCluster, BorderLayout.EAST)
    }
    // Clicking anywhere on the header (except the toggle, which handles its own click) toggles the row.
    val expandOnClick = object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.button == MouseEvent.BUTTON1) setExpanded(!expanded)
      }
    }
    listOf(header, leftCluster, chevron, iconLabel, nameLabel, summaryLabel, chainLabel).forEach {
      it.addMouseListener(expandOnClick)
    }
    return header
  }

  // ---------- Expand / collapse ----------

  private fun setExpanded(value: Boolean) {
    if (expanded == value) return
    expanded = value
    chevron.icon = if (value) AllIcons.General.ChevronDown else AllIcons.General.ChevronRight
    if (value) {
      ensureDetail()
      host.onRowExpanded(row)
      rebuildBody()
    }
    body.isVisible = value
    revalidate()
    repaint()
  }

  /** Build and embed the tool's own settings configurable once, on first expand. */
  private fun ensureDetail() {
    if (detailBuilt) return
    detailBuilt = true
    val provider = row.detailConfigurableProvider ?: return
    val configurable = row.detail ?: provider.createConfigurable(project).also { row.detail = it }
    val component = configurable.createComponent() ?: return
    configurable.reset()
    detailHolder.add(component, BorderLayout.CENTER)
    detailComponent = component
    // Reflect unapplied edits in the header summary immediately as feature checkboxes are toggled.
    collectCheckboxes(component).forEach { checkBox -> checkBox.addItemListener { updateHeader() } }
  }

  // ---------- Refresh (called by the list on state changes / probe results) ----------

  fun refresh() {
    updateHeader()
    if (expanded) rebuildBody()
    revalidate()
    repaint()
  }

  private fun updateHeader() {
    val locked = tool.isSelectedAsTypeEngine(project)
    toggle.isSelected = row.staged.enabled
    toggle.isEnabled = !locked
    if (locked) {
      toggle.setToolTipText(HtmlChunk.text(
        PyToolsUiBundle.message("settings.external.tools.locked.by.type.engine", tool.presentableName)))
    }
    else {
      toggle.toolTipText = null
    }

    // Feature/status summary: only for enabled tools. Prefer the LIVE checkbox state of the embedded
    // panel (so unapplied edits are reflected); fall back to the persisted `summaryFor` before the
    // row has ever been expanded, or for tools whose panel has no feature checkboxes (e.g. Black).
    // A non-empty list shows muted; an empty one shows the amber "select features" hint.
    val checkBoxes = detailComponent?.let(::collectCheckboxes).orEmpty()
    @NlsSafe val options: String? = when {
      !row.staged.enabled -> null
      checkBoxes.isNotEmpty() -> checkBoxes.filter { it.isSelected }.joinToString(", ") { it.text }.ifBlank { null }
      else -> tool.summaryFor(project).takeIf { it.isNotBlank() }
    }
    val noFeatures = row.staged.enabled && options == null
    summaryLabel.text = when {
      options != null -> options
      noFeatures -> PyToolsUiBundle.message("settings.external.tools.column.no.features")
      else -> ""
    }
    summaryLabel.foreground = if (noFeatures) NO_FEATURES_FOREGROUND else UIUtil.getContextHelpForeground()

    chainLabel.text = host.lookupChainHtml(row)
  }

  // ---------- Expanded body ----------

  private fun rebuildBody() {
    rebuildSdkSection()
    rebuildPathSection()
  }

  private fun rebuildSdkSection() {
    sdkSectionHolder.removeAll()
    val avail = row.sdkAvailability
    if (avail == null || avail.entries.isEmpty()) {
      // No project interpreters (or still probing) — nothing to install into, so hide the section.
      sdkSectionHolder.isVisible = false
      return
    }
    val entries = JPanel(VerticalLayout(JBUI.scale(2)))
    avail.entries.forEach { entries.add(sdkEntryLine(it)) }
    sdkSectionHolder.add(sectionLabel(PyToolsUiBundle.message("settings.external.tools.body.env")), BorderLayout.WEST)
    sdkSectionHolder.add(entries, BorderLayout.CENTER)
    sdkSectionHolder.isVisible = true
  }

  private fun sdkEntryLine(entry: SdkEntry): JComponent = horizontalLine().apply {
    @NlsSafe val label = "${entry.sdkLabel}:  "
    add(JBLabel(label))
    val path = entry.binaryPath
    if (path != null) {
      @NlsSafe val pathText = path.toString()
      add(JBLabel(pathText).apply { foreground = UIUtil.getInactiveTextColor() })
      entry.version?.let { version ->
        add(Box.createHorizontalStrut(JBUI.scale(6)))
        @NlsSafe val versionText = "v${version.value}"
        add(JBLabel(versionText).apply { foreground = UIUtil.getInactiveTextColor() })
      }
    }
    else {
      add(JBLabel(PyToolsUiBundle.message("settings.external.tools.sdk.not.installed")).apply {
        foreground = UIUtil.getInactiveTextColor()
      })
      add(Box.createHorizontalStrut(JBUI.scale(8)))
      add(ActionLink(PyToolsUiBundle.message("settings.external.tools.install.link")) { host.installIntoSdk(row, entry.sdk) })
    }
  }

  private fun rebuildPathSection() {
    pathSectionHolder.removeAll()
    pathSectionHolder.add(sectionLabel(PyToolsUiBundle.message("settings.external.tools.body.path")), BorderLayout.WEST)
    pathSectionHolder.add(pathLine(), BorderLayout.CENTER)
  }

  private fun pathLine(): JComponent = horizontalLine().apply {
    val detected = row.pathFieldValue
    val (text, muted) = when (detected) {
      is PathFieldValue.Custom -> detected.path.toString() to false
      is PathFieldValue.AutoDetected -> detected.path.toString() to true
      PathFieldValue.NotFound, null -> PyToolsUiBundle.message("settings.external.tools.path.not.found") to true
    }
    @NlsSafe val valueText = text
    val valueLabel = JBLabel(valueText).apply {
      foreground = when {
        row.pathError != null -> JBColor.RED
        row.belowMinVersionMessage != null -> JBColor.ORANGE
        muted -> UIUtil.getInactiveTextColor()
        else -> UIUtil.getLabelForeground()
      }
      pathTooltip()?.let { setToolTipText(it) }
    }
    add(valueLabel)
    val detectedVersion = row.version
    if (detectedVersion != null && (detected is PathFieldValue.Custom || detected is PathFieldValue.AutoDetected)) {
      add(Box.createHorizontalStrut(JBUI.scale(6)))
      @NlsSafe val versionText = "v${detectedVersion.value}"
      add(JBLabel(versionText).apply { foreground = UIUtil.getInactiveTextColor() })
    }
    add(Box.createHorizontalStrut(JBUI.scale(8)))

    when (iconKindFor(row, detected) { host.isUpgradeAvailable(it) }) {
      PathIconKind.INSTALL ->
        add(ActionLink(PyToolsUiBundle.message("settings.external.tools.install.link")) { host.installOnPath(row) })
      PathIconKind.UPGRADE ->
        add(ActionLink(PyToolsUiBundle.message("settings.external.tools.upgrade.link")) { host.upgradeOnPath(row) })
      PathIconKind.RESET ->
        add(ActionLink(PyToolsUiBundle.message("settings.external.tools.path.reset.tooltip")) { host.resetPath(row) })
      PathIconKind.NONE -> Unit
    }
    add(Box.createHorizontalStrut(JBUI.scale(8)))
    add(browseButton())
  }

  private fun browseButton(): JComponent = JLabel(AllIcons.General.OpenDisk).apply {
    setToolTipText(HtmlChunk.text(PyToolsUiBundle.message("settings.external.tools.path.edit.tooltip")))
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.button == MouseEvent.BUTTON1) host.browsePath(row)
      }
    })
  }

  /** Full path + version + below-minimum + validation-error, or `null` when there is nothing to show. */
  @Suppress("HardCodedStringLiteral")
  private fun pathTooltip(): HtmlChunk? {
    val builder = HtmlBuilder()
    var has = false
    fun line(text: String) {
      if (has) builder.br()
      builder.append(text)
      has = true
    }
    when (val d = row.pathFieldValue) {
      is PathFieldValue.Custom -> line(d.path.toString())
      is PathFieldValue.AutoDetected -> line(d.path.toString())
      PathFieldValue.NotFound, null -> Unit
    }
    row.version?.let { line(PyToolsUiBundle.message("settings.external.tools.path.version.tooltip", it.toString())) }
    row.belowMinVersionMessage?.let { line(it) }
    row.pathError?.let { line(it) }
    return if (has) builder.wrapWith(HtmlChunk.html()) else null
  }

  // ---------- Search spotlight ----------

  fun setSpotlight(on: Boolean) {
    if (on) {
      border = BorderFactory.createLineBorder(searchSpotlightBorderColor(), JBUI.scale(2))
    }
    else {
      applyNormalBorder()
    }
    repaint()
  }

  private fun applyNormalBorder() {
    border = JBUI.Borders.customLineBottom(JBColor.border())
  }

  // ---------- Small layout helpers ----------

  private fun sectionLabel(@NlsSafe text: String): JComponent = JBLabel(text).apply {
    verticalAlignment = SwingConstants.TOP
    border = JBUI.Borders.emptyRight(JBUI.scale(8))
    preferredSize = Dimension(JBUI.scale(52), preferredSize.height)
    minimumSize = preferredSize
  }

  private fun horizontalLine(): JPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    isOpaque = false
  }

  /** All [JCheckBox]es anywhere inside [root] (the embedded feature panel), in traversal order. */
  private fun collectCheckboxes(root: Component): List<JCheckBox> {
    val result = mutableListOf<JCheckBox>()
    fun walk(c: Component) {
      if (c is JCheckBox) result.add(c)
      if (c is Container) c.components.forEach(::walk)
    }
    walk(root)
    return result
  }
}
