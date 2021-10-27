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
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils
import org.intellij.plugins.markdown.lang.MarkdownFileType
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import kotlin.math.floor

internal class InsertEmptyTableAction: DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val editor = event.getRequiredData(CommonDataKeys.EDITOR)
    val hintComponent = TableGridComponent { rows, columns ->
      val text = TableModificationUtils.buildEmptyTable(rows, columns)
      runWriteAction {
        executeCommand(event.project) {
          val caret = editor.caretModel.currentCaret
          val document = editor.document
          val currentLine = document.getLineNumber(caret.offset)
          val content = when {
            currentLine != 0 && !DocumentUtil.isLineEmpty(document, currentLine - 1) -> "\n$text"
            else -> text
          }
          EditorModificationUtil.insertStringAtCaret(editor, content)
        }
      }
    }
    val hint = LightweightHint(hintComponent)
    hintComponent.parentHint = hint
    hint.setForceShowAsPopup(true)
    val hintManager = HintManagerImpl.getInstanceImpl()
    val position = hintManager.getHintPosition(hint, editor, HintManager.DEFAULT)
    hint.setFocusRequestor(hintComponent)
    hintManager.showEditorHint(
      hint,
      editor,
      position,
      HintManager.HIDE_BY_ESCAPE or HintManager.UPDATE_BY_SCROLLING,
      0,
      true
    )
  }

  override fun update(event: AnActionEvent) {
    val editor = event.getData(CommonDataKeys.EDITOR)
    val file = event.getData(CommonDataKeys.PSI_FILE)
    event.presentation.isEnabledAndVisible = editor != null && file?.fileType == MarkdownFileType.INSTANCE
  }

  private class TableGridComponent(
    private var rows: Int = 4,
    private var columns: Int = 4,
    private val expandFactor: Int = 2,
    private val selectedCallback: (Int, Int) -> Unit
  ): JPanel(MigLayout("insets 8")) {
    lateinit var parentHint: LightweightHint
    private val cells = arrayListOf<ArrayList<Cell>>()
    private var selectedCellRow = 0
    private var selectedCellColumn = 0

    private val gridPanel = JPanel(MigLayout("insets 0, gap 3"))
    private val label = JBLabel()

    private val mouseListener = MyMouseListener()
    private val childMouseListener = object: MouseAdapter() {
      override fun mouseClicked(event: MouseEvent) {
        mouseListener.mouseClicked(SwingUtilities.convertMouseEvent(event.component, event, this@TableGridComponent))
      }
    }

    init {
      for (rowIndex in 0 until rows) {
        cells.add(generateSequence { Cell() }.take(columns).toCollection(ArrayList(columns)))
      }
      fillGrid()
      add(gridPanel, "wrap")
      add(label, "align center")
      gridPanel.addMouseMotionListener(mouseListener)
      gridPanel.addMouseListener(mouseListener)
      registerAction(KeyEvent.VK_RIGHT, "selectRight", ArrowAction { 0 to 1 })
      registerAction(KeyEvent.VK_LEFT, "selectLeft", ArrowAction { 0 to -1 })
      registerAction(KeyEvent.VK_UP, "selectUp", ArrowAction { -1 to 0 })
      registerAction(KeyEvent.VK_DOWN, "selectDown", ArrowAction { 1 to 0})
      registerAction(KeyEvent.VK_ENTER, "confirmSelection", object: AbstractAction() {
        override fun actionPerformed(event: ActionEvent) {
          parentHint.hide()
          selectedCallback.invoke(selectedCellRow, selectedCellColumn)
        }
      })
      updateSelection(0, 0)
    }

    private fun registerAction(key: Int, actionKey: String, action: Action) {
      val inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW)
      inputMap.put(KeyStroke.getKeyStroke(key, 0), actionKey)
      actionMap.put(actionKey, action)
    }

    private fun fillGrid() {
      for (row in 0 until rows) {
        for (column in 0 until columns) {
          val cell = cells[row][column]
          when {
            column == columns - 1 && row != rows - 1 -> gridPanel.add(cell, "wrap")
            else -> gridPanel.add(cell)
          }
        }
      }
    }

    private fun expandGrid(expandRows: Boolean, expandColumns: Boolean) {
      gridPanel.removeAll()
      if (expandRows) {
        repeat(expandFactor) {
          cells.add(generateSequence { Cell() }.take(columns).toCollection(ArrayList(columns)))
        }
        rows += expandFactor
      }
      if (expandColumns) {
        for (row in cells) {
          repeat(expandFactor) {
            row.add(Cell())
          }
        }
        columns += expandFactor
      }
      fillGrid()
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
      val shouldExpandRows = rows < maxRows && selectedRow + 1 == rows
      val shouldExpandColumns = columns < maxColumns && selectedColumn + 1 == columns
      if (shouldExpandRows || shouldExpandColumns) {
        expandGrid(expandRows = shouldExpandRows, expandColumns = shouldExpandColumns)
        parentHint.pack()
        parentHint.component.revalidate()
        parentHint.component.repaint()
      }
    }

    private inner class ArrowAction(private val calcDiff: () -> Pair<Int, Int>): AbstractAction() {
      override fun actionPerformed(event: ActionEvent) {
        val (rowDiff, columnDiff) = calcDiff.invoke()
        var row = selectedCellRow + rowDiff
        var column = selectedCellColumn + columnDiff
        row = row.coerceAtMost(rows - 1).coerceAtLeast(0)
        column = column.coerceAtMost(columns - 1).coerceAtLeast(0)
        updateSelection(row, column)
      }
    }

    private inner class MyMouseListener: MouseAdapter() {
      private fun obtainIndices(point: Point): Pair<Int, Int> {
        val panelWidth = gridPanel.width.toFloat()
        val panelHeight = gridPanel.height.toFloat()
        val tileWidth = panelWidth / columns
        val tileHeight = panelHeight / rows
        val column = floor(point.x.toFloat() / tileWidth).toInt()
        val row = floor(point.y.toFloat() / tileHeight).toInt()
        return row to column
      }

      override fun mouseMoved(event: MouseEvent) {
        val (row, column) = obtainIndices(event.point)
        updateSelection(row, column)
      }

      override fun mouseClicked(event: MouseEvent) {
        val (row, column) = obtainIndices(event.point)
        updateSelection(row, column)
        selectedCallback.invoke(row, column)
      }
    }

    private inner class Cell: JPanel() {
      init {
        background = UIUtil.getTextFieldBackground()
        size = Dimension(15, 15)
        preferredSize = size
        border = IdeBorderFactory.createBorder()
        border = IdeBorderFactory.createBorder()
        addMouseListener(childMouseListener)
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
