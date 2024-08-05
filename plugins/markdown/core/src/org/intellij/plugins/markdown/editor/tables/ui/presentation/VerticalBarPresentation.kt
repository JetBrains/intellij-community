// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.ui.presentation

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hints.presentation.BasePresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.impl.ToolbarUtils
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.siblings
import com.intellij.psi.util.startOffset
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.GraphicsUtil
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils.isSoftWrapping
import org.intellij.plugins.markdown.editor.tables.TableUtils
import org.intellij.plugins.markdown.editor.tables.TableUtils.isHeaderRow
import org.intellij.plugins.markdown.editor.tables.TableUtils.isLast
import org.intellij.plugins.markdown.editor.tables.actions.TableActionKeys
import org.intellij.plugins.markdown.editor.tables.actions.TableActionPlaces
import org.intellij.plugins.markdown.editor.tables.ui.presentation.GraphicsUtils.clearHalfOvalOverEditor
import org.intellij.plugins.markdown.editor.tables.ui.presentation.GraphicsUtils.fillHalfOval
import org.intellij.plugins.markdown.editor.tables.ui.presentation.GraphicsUtils.useCopy
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import javax.swing.SwingUtilities

internal class VerticalBarPresentation(
  private val editor: Editor,
  private val row: PsiElement,
  private val hover: Boolean,
  private val accent: Boolean = false
): BasePresentation() {
  private data class BoundsState(
    val width: Int,
    val height: Int
  )

  private var boundsState = initialState

  init {
    PsiDocumentManager.getInstance(row.project).performForCommittedDocument(editor.document) {
      invokeLater(ModalityState.stateForComponent(editor.contentComponent)) {
        if (shouldShowInlay()) {
          val calculated = BoundsState(barWidth, editor.lineHeight)
          boundsState = calculated
          fireSizeChanged(Dimension(0, 0), Dimension(calculated.width, calculated.height))
        }
      }
    }
  }

  private fun shouldShowInlay(): Boolean {
    if (!row.isValid || editor.isDisposed) {
      return false
    }
    val table = TableUtils.findTable(row) ?: return false
    return !table.isSoftWrapping(editor)
  }

  override val width
    get() = boundsState.width

  override val height
    get() = boundsState.height

  private enum class RowLocation {
    FIRST,
    LAST,
    OTHER
  }

  private val rowLocation
    get() = when {
      (row as? MarkdownTableRow)?.isHeaderRow == true -> RowLocation.FIRST
      (row as? MarkdownTableRow)?.isLast == true -> RowLocation.LAST
      row is MarkdownTableSeparatorRow && !hasRowAfter(row) -> RowLocation.LAST
      else -> RowLocation.OTHER
    }

  private fun hasRowAfter(element: PsiElement): Boolean {
    return element.siblings(forward = true).find { it is MarkdownTableRow } != null
  }

  override fun paint(graphics: Graphics2D, attributes: TextAttributes) {
    if (!row.isValid || editor.isDisposed || boundsState == initialState) {
      return
    }
    graphics.useCopy { local ->
      GraphicsUtil.setupAntialiasing(local)
      GraphicsUtil.setupRoundedBorderAntialiasing(local)
      paintRow(local, rowLocation)
    }
  }

  private fun calculateBarRect(location: RowLocation): Rectangle {
    return when (location) {
      RowLocation.OTHER -> Rectangle(0, 0, barWidth, height)
      RowLocation.FIRST -> Rectangle(0, barWidth / 2, barWidth, height - barWidth / 2)
      RowLocation.LAST -> Rectangle(0, 0, barWidth, height - barWidth / 2)
    }
  }

  private fun paintRow(graphics: Graphics2D, location: RowLocation) {
    val rect = calculateBarRect(location)
    actuallyPaintBar(graphics, rect, hover, accent)
    graphics.color = TableInlayProperties.circleColor
    when (location) {
      RowLocation.OTHER -> paintOtherCircles(graphics, rect)
      RowLocation.FIRST -> paintFirstCircles(graphics, rect)
      RowLocation.LAST -> paintLastCircles(graphics, rect)
    }
  }

  private fun paintOtherCircles(graphics: Graphics2D, rect: Rectangle) {
    repeat(2) {
      graphics.fillHalfOval(rect.x, rect.y - barWidth / 2, barWidth, barWidth, upperHalf = true)
      graphics.fillHalfOval(rect.x, rect.y + rect.height - barWidth / 2, barWidth, barWidth, upperHalf = false)
    }
  }

  private fun paintFirstCircles(graphics: Graphics2D, rect: Rectangle) {
    repeat(2) {
      graphics.fillOval(rect.x, rect.y - barWidth / 2, barWidth, barWidth)
      graphics.fillHalfOval(rect.x, rect.y + rect.height - barWidth / 2, barWidth, barWidth, upperHalf = false)
    }
  }

  private fun paintLastCircles(graphics: Graphics2D, rect: Rectangle) {
    repeat(2) {
      graphics.fillHalfOval(rect.x, rect.y - barWidth / 2, barWidth, barWidth, upperHalf = true)
      graphics.fillOval(rect.x, rect.y + rect.height - barWidth / 2, barWidth, barWidth)
    }
  }

  private fun actuallyPaintBar(graphics: Graphics2D, rect: Rectangle, hover: Boolean, accent: Boolean) {
    val paintCount = when {
      accent -> 2
      else -> 1
    }
    repeat(paintCount) {
      graphics.color = when {
        hover -> TableInlayProperties.barHoverColor
        else -> TableInlayProperties.barColor
      }
      graphics.fillRect(rect.x, rect.y, rect.width, rect.height)
    }
    graphics.clearHalfOvalOverEditor(rect.x, rect.y - barWidth / 2, barWidth, barWidth, upper = true)
    graphics.clearHalfOvalOverEditor(rect.x, rect.y + rect.height - barWidth / 2, barWidth, barWidth, upper = false)
  }

  override fun mouseClicked(event: MouseEvent, translated: Point) {
    when {
      SwingUtilities.isLeftMouseButton(event) && event.clickCount.mod(2) == 0 -> handleMouseLeftDoubleClick(event, translated)
      SwingUtilities.isLeftMouseButton(event) -> handleMouseLeftClick(event, translated)
    }
  }

  private fun handleMouseLeftClick(event: MouseEvent, translated: Point) {
    showToolbar()
  }

  private fun handleMouseLeftDoubleClick(event: MouseEvent, translated: Point) {
    event.consume()
    // select row
    executeCommand(row.project) {
      val textRange = row.textRange
      with(editor.caretModel.currentCaret) {
        moveToOffset(textRange.startOffset)
        setSelection(textRange.startOffset, textRange.endOffset)
      }
    }
  }

  override fun toString() = "VerticalBarPresentation"

  private fun calculateToolbarPosition(componentHeight: Int): Point {
    val position = editor.offsetToXY(row.startOffset)
    // Position hint relative to the editor
    val editorParent = editor.contentComponent.topLevelAncestor.locationOnScreen
    val editorPosition = editor.contentComponent.locationOnScreen
    position.translate(editorPosition.x - editorParent.x, editorPosition.y - editorParent.y)
    // Translate hint right above the bar
    position.translate(0, -editor.lineHeight)
    position.translate(0, -componentHeight)
    val rect = calculateBarRect(rowLocation)
    val bottomPadding = 2
    position.translate(rect.x, -rect.y - barWidth * 2 - bottomPadding)
    return position
  }

  private fun showToolbar() {
    val targetComponent = ToolbarUtils.createTargetComponent(editor) { sink ->
      uiDataSnapshot(sink, row)
    }
    ToolbarUtils.createImmediatelyUpdatedToolbar(
      group = rowActionGroup,
      place = TableActionPlaces.TABLE_INLAY_TOOLBAR,
      targetComponent,
      horizontal = true,
      onUpdated = this::createAndShowHint
    )
  }

  private fun createAndShowHint(toolbar: ActionToolbar) {
    val hint = LightweightHint(toolbar.component)
    hint.setForceShowAsPopup(true)
    val targetPoint = calculateToolbarPosition(hint.component.preferredSize.height)
    val hintManager = HintManagerImpl.getInstanceImpl()
    hintManager.hideAllHints()
    val flags = HintManager.HIDE_BY_ESCAPE or HintManager.HIDE_BY_SCROLLING or HintManager.HIDE_BY_CARET_MOVE or HintManager.HIDE_BY_TEXT_CHANGE
    hintManager.showEditorHint(hint, editor, targetPoint, flags, 0, false)
  }

  companion object {
    // should be even
    const val barWidth = TableInlayProperties.barSize
    //const val borderRadius = barWidth

    private val rowActionGroup
      get() = ActionManager.getInstance().getAction("Markdown.TableRowActions") as ActionGroup

    private val initialState = BoundsState(0, 0)

    private fun uiDataSnapshot(sink: DataSink, row: PsiElement) {
      val elementReference = WeakReference(row)
      sink.lazy(TableActionKeys.ELEMENT) { elementReference }
    }

    private fun wrapPresentation(factory: PresentationFactory, editor: Editor, presentation: InlayPresentation): InlayPresentation {
      return factory.inset(
        PresentationWithCustomCursor(editor, presentation),
        left = TableInlayProperties.leftRightPadding,
        right = TableInlayProperties.leftRightPadding
      )
    }

    fun create(factory: PresentationFactory, editor: Editor, row: PsiElement): InlayPresentation {
      return factory.changeOnHover(
        wrapPresentation(factory, editor, VerticalBarPresentation(editor, row, false)),
        { wrapPresentation(factory, editor, VerticalBarPresentation(editor, row, true)) }
      )
    }
  }
}
