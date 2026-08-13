// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui.packagemanagers

import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.pytools.ui.PyToolsUiBundle
import com.intellij.python.pytools.ui.configuration.PathFieldValue
import com.intellij.python.pytools.ui.configuration.PathIconKind
import com.intellij.python.pytools.ui.configuration.ToolRow
import com.intellij.python.pytools.ui.configuration.fixedWidthPanel
import com.intellij.python.pytools.ui.configuration.iconKindFor
import com.intellij.python.pytools.ui.configuration.installedVersionLabel
import com.intellij.python.pytools.ui.configuration.pathDetailsTooltip
import com.intellij.python.pytools.ui.configuration.searchSpotlightBorderColor
import com.intellij.python.pytools.ui.configuration.upgradeLinkText
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * One flat row on the Package Managers page: the tool's icon + name on the left (fixed Tool column)
 * and its executable path with inline install / upgrade / revert / browse actions on the right.
 * No expand, no toggle — package managers have no per-tool feature settings.
 */
internal class PyPackageManagerRowPanel(
  private val row: ToolRow,
  private val host: PmHost,
) : JPanel(BorderLayout()) {

  private val tool get() = row.tool
  private val pathHolder = JPanel(BorderLayout())

  init {
    val nameLabel = JBLabel(tool.presentableName).apply {
      font = JBFont.label().asBold()
      setToolTipText(HtmlChunk.text(tool.description))
    }
    val toolColumn = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      isOpaque = false
      add(JLabel(tool.icon))
      add(Box.createHorizontalStrut(JBUI.scale(6)))
      add(nameLabel)
    }
    val header = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty(6, 8)
      isOpaque = false
      add(fixedWidthPanel(pmToolColumnWidth(), toolColumn), BorderLayout.WEST)
      add(pathHolder, BorderLayout.CENTER)
    }
    add(header, BorderLayout.NORTH)
    applyNormalBorder()
    refresh()
  }

  fun refresh() {
    pathHolder.removeAll()
    pathHolder.add(pathLine(), BorderLayout.CENTER)
    revalidate()
    repaint()
  }

  private fun pathLine(): JComponent = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    isOpaque = false
    val detected = row.pathFieldValue
    val (text, muted) = when (detected) {
      is PathFieldValue.Custom -> detected.path.toString() to false
      is PathFieldValue.AutoDetected -> detected.path.toString() to true
      PathFieldValue.NotFound, null -> PyToolsUiBundle.message("settings.external.tools.path.not.found") to true
    }
    @NlsSafe val valueText = text
    add(JBLabel(valueText).apply {
      foreground = when {
        row.pathError != null -> JBColor.RED
        row.belowMinVersionMessage != null -> JBColor.ORANGE
        muted -> UIUtil.getInactiveTextColor()
        else -> UIUtil.getLabelForeground()
      }
      pathDetailsTooltip(row)?.let { setToolTipText(it) }
    })
    installedVersionLabel(row)?.let { add(Box.createHorizontalStrut(JBUI.scale(6))); add(it) }
    add(Box.createHorizontalStrut(JBUI.scale(10)))
    val canInstall = tool.manager?.canInstall(host.project.getEelDescriptor()) == true
    when (iconKindFor(row, detected, canInstall) { host.isUpgradeAvailable(it) }) {
      PathIconKind.INSTALL ->
        add(ActionLink(PyToolsUiBundle.message("settings.external.tools.install.link")) { host.installOnPath(row) })
      PathIconKind.UPGRADE ->
        add(ActionLink(upgradeLinkText(host.upgradeTargetVersion(row))) { host.upgradeOnPath(row) })
      PathIconKind.RESET ->
        add(ActionLink(PyToolsUiBundle.message("settings.external.tools.path.reset.tooltip")) { host.resetPath(row) })
      PathIconKind.NONE -> Unit
    }
    add(Box.createHorizontalStrut(JBUI.scale(8)))
    add(browseButton())
    add(Box.createHorizontalGlue())
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
}
