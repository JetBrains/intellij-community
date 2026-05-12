// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lsp.ui.settings

import com.intellij.lsp.ui.LspUiBundle
import com.intellij.openapi.fileTypes.FileNameMatcher
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.ListUtil
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class LspPatternsPanel : JPanel(BorderLayout()) {
  private val myList = JBList(DefaultListModel<FileNameMatcher>())
  private var patterns: MutableList<FileNameMatcher> = mutableListOf()

  init {
    myList.selectionMode = ListSelectionModel.SINGLE_SELECTION
    myList.cellRenderer = PatternRenderer()
    myList.emptyText.text = LspUiBundle.message("lsp.settings.server.file.patterns.empty")
    myList.border = JBUI.Borders.empty()

    val decorator = ToolbarDecorator.createDecorator(myList)
      .setScrollPaneBorder(JBUI.Borders.empty())
      .setPanelBorder(JBUI.Borders.customLine(JBColor.border(), 1, 1, 0, 1))
      .setAddAction { addPattern() }
      .setEditAction { editPattern() }
      .setRemoveAction { removePattern() }
      .disableUpDownActions()

    add(decorator.createPanel(), BorderLayout.NORTH)

    val scrollPane = JBScrollPane(myList)
    scrollPane.border = JBUI.Borders.customLine(JBColor.border(), 0, 1, 1, 1)
    add(scrollPane, BorderLayout.CENTER)

    border = IdeBorderFactory.createTitledBorder(
      LspUiBundle.message("lsp.settings.server.file.patterns"),
      false,
      JBUI.insetsTop(8)
    ).setShowLine(false)
  }

  fun getPatterns(): String {
    return patterns.joinToString(";") { it.presentableString }
  }

  fun setPatterns(patternsString: String) {
    patterns.clear()
    val model = myList.model as DefaultListModel<FileNameMatcher>
    model.clear()

    if (patternsString.isNotBlank()) {
      val patternsList = patternsString.split(";").filter { it.isNotBlank() }
      patternsList.forEach { pattern ->
        val matcher = parsePattern(pattern)
        patterns.add(matcher)
        model.addElement(matcher)
      }
    }
  }

  private fun addPattern() {
    editPattern(null)
  }

  private fun editPattern() {
    val item = myList.selectedValue
    if (item != null) {
      editPattern(item)
    }
  }

  private fun editPattern(item: FileNameMatcher?) {
    val title = if (item == null) {
      LspUiBundle.message("lsp.settings.server.pattern.add.title")
    }
    else {
      LspUiBundle.message("lsp.settings.server.pattern.edit.title")
    }

    val oldPattern = item?.presentableString
    val pattern = Messages.showInputDialog(
      this,
      LspUiBundle.message("lsp.settings.server.pattern.prompt"),
      title,
      null,
      oldPattern,
      null
    )

    if (pattern != null && pattern.isNotBlank()) {
      val matcher = parsePattern(pattern)
      val model = myList.model as DefaultListModel<FileNameMatcher>
      if (item != null) {
        val index = patterns.indexOf(item)
        if (index >= 0) {
          patterns[index] = matcher
          model.set(index, matcher)
        }
      }
      else {
        patterns.add(matcher)
        model.addElement(matcher)
      }
      myList.setSelectedValue(matcher, true)
    }
  }

  private fun removePattern() {
    val removed = myList.selectedValue ?: return
    patterns.remove(removed)
    ListUtil.removeSelectedItems(myList)
  }

  private class PatternRenderer : ColoredListCellRenderer<FileNameMatcher>() {
    override fun customizeCellRenderer(
      list: JList<out FileNameMatcher>,
      value: FileNameMatcher?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean,
    ) {
      if (value != null) {
        append(value.presentableString)
      }
    }
  }

  companion object {
    fun parsePattern(pattern: String): FileNameMatcher {
      return FileNameMatcherFactory.getInstance().createMatcher(pattern)
    }
  }
}