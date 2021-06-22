// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.actions

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.LightweightHint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

internal class InsertEmptyTableAction: DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val editor = event.getRequiredData(CommonDataKeys.EDITOR)
    val hintComponent = TableGridComponent { rows, columns ->
      val text = TableModificationUtils.buildEmptyTable(rows, columns)
      runWriteAction {
        executeCommand(event.project) {
          EditorModificationUtil.insertStringAtCaret(editor, text)
        }
      }
    }
    val hint = LightweightHint(hintComponent)
    hintComponent.parentHint = hint
    hint.setForceShowAsPopup(true)
    val hintManager = HintManagerImpl.getInstanceImpl()
    val position = hintManager.getHintPosition(hint, editor, HintManager.DEFAULT)
    hintManager.showEditorHint(
      hint,
      editor,
      position,
      HintManager.HIDE_BY_ESCAPE or HintManager.UPDATE_BY_SCROLLING or HintManager.HIDE_BY_ANY_KEY,
      0,
      true
    )
  }

  override fun update(event: AnActionEvent) {
    val editor = event.getData(CommonDataKeys.EDITOR)
    if (editor == null) {
      event.presentation.isEnabledAndVisible = false
    }
  }

  private class TableGridComponent(
    private var rows: Int = 4,
    private var columns: Int = 4,
    private val expandFactor: Int = 2,
    private val selectedCallback: (Int, Int) -> Unit
  ): JPanel(MigLayout("insets 8, gap 3")) {
    lateinit var parentHint: LightweightHint
    private val cells = arrayListOf<ArrayList<Cell>>()
    private var selectedCellRow = 0
    private var selectedCellColumn = 0

    private val label = JBLabel()

    init {
      for (rowIndex in 0 until rows) {
        val row = ArrayList<Cell>(columns)
        for (columnIndex in 0 until columns) {
          val cell = Cell()
          addCellListeners(cell, rowIndex, columnIndex)
          row.add(cell)
        }
        cells.add(row)
      }
      fillGrid()
      updateSelection(0, 0)
    }

    private fun fillGrid() {
      for (row in 0 until rows) {
        for (column in 0 until columns) {
          val cell = cells[row][column]
          when (column) {
            columns - 1 -> add(cell, "wrap")
            else -> add(cell)
          }
        }
      }
      add(label, "span, align center")
    }

    private fun expandGrid() {
      removeAll()
      for ((index, row) in cells.withIndex()) {
        repeat(expandFactor) {
          val cell = Cell()
          addCellListeners(cell, index, columns - 1 + it)
          row.add(Cell())
        }
      }
      repeat(expandFactor) {
        val row = ArrayList<Cell>(columns - 1 + expandFactor)
        for (index in 0 until (rows + expandFactor)) {
          val cell = Cell()
          addCellListeners(cell, cells.lastIndex + 1, index)
          row.add(cell)
        }
        cells.add(row)
      }
      rows += expandFactor
      columns += expandFactor
      fillGrid()
    }

    private fun addCellListeners(cell: Cell, row: Int, column: Int) {
      cell.addMouseListener(object: MouseAdapter() {
        override fun mouseEntered(event: MouseEvent) {
          updateSelection(row, column)
        }

        override fun mouseClicked(event: MouseEvent) {
          updateSelection(row, column)
          selectedCallback.invoke(selectedCellRow + 1, selectedCellColumn + 1)
        }
      })
    }

    private fun updateSelection(selectedRow: Int, selectedColumn: Int) {
      selectedCellRow = selectedRow
      selectedCellColumn = selectedColumn
      @Suppress("HardCodedStringLiteral")
      label.text = "${selectedRow + 1}×${selectedColumn + 1}"
      for (row in 0 until rows) {
        for (column in 0 until columns) {
          cells[row][column].isSelected = row <= selectedCellRow && column <= selectedCellColumn
        }
      }
      repaint()
      if (selectedRow + 1 == rows && selectedColumn + 1 == columns && rows < maxRows && columns < maxColumns) {
        expandGrid()
        parentHint.pack()
        parentHint.component.revalidate()
        parentHint.component.repaint()
      }
    }

    private class Cell: JPanel() {
      init {
        background = UIUtil.getTextFieldBackground()
        size = Dimension(15, 15)
        preferredSize = size
        border = IdeBorderFactory.createBorder()
      }

      var isSelected = false
        set(value) {
          field = value
          background = when {
            value -> UIUtil.getFocusedFillColor()
            else -> UIUtil.getEditorPaneBackground()
          }
        }
    }

    companion object {
      private const val maxRows = 10
      private const val maxColumns = 10
    }
  }
}
