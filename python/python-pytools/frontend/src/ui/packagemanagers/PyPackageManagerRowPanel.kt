// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.frontend.ui.packagemanagers

import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.python.pytools.frontend.ui.PyToolsUiBundle
import com.intellij.python.pytools.frontend.ui.configuration.ToolRow
import com.intellij.python.pytools.frontend.ui.configuration.fixedWidthPanel
import com.intellij.python.pytools.frontend.ui.configuration.installedVersionLabel
import com.intellij.python.pytools.frontend.ui.configuration.pathActionLink
import com.intellij.python.pytools.frontend.ui.configuration.pathValueLabel
import com.intellij.python.pytools.frontend.ui.configuration.searchSpotlightBorderColor
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
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
    add(pathValueLabel(row))
    installedVersionLabel(row)?.let { add(Box.createHorizontalStrut(JBUI.scale(6))); add(it) }
    add(Box.createHorizontalStrut(JBUI.scale(10)))
    pathActionLink(row, detected, host)?.let { add(it) }
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
