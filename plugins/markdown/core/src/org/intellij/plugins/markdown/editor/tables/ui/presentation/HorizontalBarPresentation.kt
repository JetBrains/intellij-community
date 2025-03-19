// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.ui.presentation

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hints.fireUpdateEvent
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
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.siblings
import com.intellij.psi.util.startOffset
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.GraphicsUtil
import org.intellij.plugins.markdown.editor.tables.TableFormattingUtils.isSoftWrapping
import org.intellij.plugins.markdown.editor.tables.actions.TableActionKeys
import org.intellij.plugins.markdown.editor.tables.actions.TableActionPlaces
import org.intellij.plugins.markdown.editor.tables.selectColumn
import org.intellij.plugins.markdown.editor.tables.ui.presentation.GraphicsUtils.clearOvalOverEditor
import org.intellij.plugins.markdown.editor.tables.ui.presentation.GraphicsUtils.useCopy
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import org.intellij.plugins.markdown.lang.psi.util.hasType
import java.awt.*
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import javax.swing.SwingUtilities

internal class HorizontalBarPresentation(private val editor: Editor, private val table: MarkdownTable): BasePresentation() {
  private data class BoundsState(
    val width: Int,
    val height: Int,
    val barsModel: List<Rectangle>
  )

  private var lastSelectedIndex: Int? = null
  private var boundsState = emptyBoundsState

  init {
    val document = editor.document
    PsiDocumentManager.getInstance(table.project).performForCommittedDocument(document) {
      invokeLater(ModalityState.stateForComponent(editor.contentComponent)) {
        if (!isInvalid && !table.isSoftWrapping(editor)) {
          val calculated = calculateCurrentBoundsState(document)
          boundsState = calculated
          fireSizeChanged(Dimension(0, 0), Dimension(calculated.width, calculated.height))
        }
      }
    }
  }

  private val barsModel
    get() = boundsState.barsModel

  private val isInvalid
    get() = !table.isValid || editor.isDisposed

  override val width
    get() = boundsState.width

  override val height
    get() = boundsState.height

  override fun paint(graphics: Graphics2D, attributes: TextAttributes) {
    if (isInvalid) {
      return
    }
    graphics.useCopy { local ->
      GraphicsUtil.setupAntialiasing(local)
      GraphicsUtil.setupRoundedBorderAntialiasing(local)
      paintBars(local)
    }
  }

  override fun toString() = "HorizontalBarPresentation"

  override fun mouseClicked(event: MouseEvent, translated: Point) {
    when {
      SwingUtilities.isLeftMouseButton(event) && event.clickCount.mod(2) == 0 -> handleMouseLeftDoubleClick(event, translated)
      SwingUtilities.isLeftMouseButton(event) -> handleMouseLeftClick(event, translated)
    }
  }

  override fun mouseMoved(event: MouseEvent, translated: Point) {
    val index = determineColumnIndex(translated)
    updateSelectedIndexIfNeeded(index)
  }

  override fun mouseExited() {
    updateSelectedIndexIfNeeded(null)
  }

  private fun calculateCurrentBoundsState(document: Document): BoundsState {
    if (isInvalid) {
      return emptyBoundsState
    }
    //val document = obtainCommittedDocument(table) ?: return emptyBoundsState
    val fontsMetrics = obtainFontMetrics(editor)
    val width = calculateRowWidth(fontsMetrics, document)
    val barsModel = buildBarsModel(fontsMetrics, document)
    return BoundsState(width, barHeight, barsModel)
  }

  private fun calculateRowWidth(fontMetrics: FontMetrics, document: Document): Int {
    if (isInvalid) {
      return 0
    }
    val header = table.headerRow ?: return 0
    return fontMetrics.stringWidth(document.getText(header.textRange))
  }

  private fun updateSelectedIndexIfNeeded(index: Int?) {
    if (lastSelectedIndex != index) {
      lastSelectedIndex = index
      // Force full re-render by lying about previous dimensions
      fireUpdateEvent(Dimension(0, 0))
    }
  }

  private fun buildBarsModel(fontMetrics: FontMetrics, document: Document): List<Rectangle> {
    val header = requireNotNull(table.headerRow)
    val positions = calculatePositions(header, document, fontMetrics)
    val sectors = buildSectors(positions)
    return sectors.map { (offset, width) -> Rectangle(offset - barHeight / 2, 0, width + barHeight, barHeight) }
  }

  private fun calculatePositions(header: MarkdownTableRow, document: Document, fontMetrics: FontMetrics): List<Int> {
    require(barHeight % 2 == 0) { "barHeight value should be even" }
    val separators = header.firstChild.siblings(forward = true, withSelf = true)
      .filter { it.hasType(MarkdownTokenTypes.TABLE_SEPARATOR) && it !is MarkdownTableSeparatorRow }
      .map { it.startOffset }
    val separatorWidth = fontMetrics.charWidth('|')
    val firstOffset = separators.firstOrNull() ?: return emptyList()
    val result = ArrayList<Int>()
    var position = editor.offsetToXY(firstOffset).x + separatorWidth / 2
    var lastOffset = firstOffset
    result.add(position)
    for (offset in separators.drop(1)) {
      val length = fontMetrics.stringWidth(document.getText(TextRange(lastOffset, offset)))
      position += length
      result.add(position)
      lastOffset = offset
    }
    return result
  }

  private fun buildSectors(positions: List<Int>): List<Pair<Int, Int>> {
    return positions.windowed(2).map { (left, right) -> left to (right - left) }.toList()
  }

  private fun determineColumnIndex(point: Point): Int? {
    return barsModel.indexOfFirst { it.contains(point) }.takeUnless { it < 0 }
  }

  private fun calculateToolbarPosition(componentHeight: Int, columnIndex: Int): Point {
    val position = editor.offsetToXY(table.startOffset)
    // Position hint relative to the editor
    val editorParent = editor.contentComponent.topLevelAncestor.locationOnScreen
    val editorPosition = editor.contentComponent.locationOnScreen
    position.translate(editorPosition.x - editorParent.x, editorPosition.y - editorParent.y)
    // Translate hint right above the bar
    position.translate(leftPadding, -editor.lineHeight)
    position.translate(0, -componentHeight)
    val rect = barsModel[columnIndex]
    val bottomPadding = 2
    position.translate(rect.x, -rect.y - barHeight * 2 - bottomPadding)
    return position
  }

  private fun showToolbar(columnIndex: Int) {
    val targetComponent = ToolbarUtils.createTargetComponent(editor) { sink ->
      uiDataSnapshot(sink, table, columnIndex)
    }
    ToolbarUtils.createImmediatelyUpdatedToolbar(
      group = columnActionGroup,
      place = TableActionPlaces.TABLE_INLAY_TOOLBAR,
      targetComponent,
      horizontal = true,
      onUpdated = { createAndShowHint(it, columnIndex) }
    )
  }

  private fun createAndShowHint(toolbar: ActionToolbar, columnIndex: Int) {
    val hint = LightweightHint(toolbar.component)
    hint.setForceShowAsPopup(true)
    val targetPoint = calculateToolbarPosition(hint.component.preferredSize.height, columnIndex)
    val hintManager = HintManagerImpl.getInstanceImpl()
    hintManager.hideAllHints()
    val flags = HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_SCROLLING or HintManager.HIDE_BY_CARET_MOVE or HintManager.HIDE_BY_TEXT_CHANGE
    hintManager.showEditorHint(hint, editor, targetPoint, flags, 0, false)
  }

  private fun handleMouseLeftDoubleClick(event: MouseEvent, translated: Point) {
    val columnIndex = determineColumnIndex(translated) ?: return
    invokeLater {
      executeCommand {
        table.selectColumn(editor, columnIndex, withHeader = true, withSeparator = true, withBorders = true)
      }
    }
  }

  private fun handleMouseLeftClick(event: MouseEvent, translated: Point) {
    val columnIndex = determineColumnIndex(translated) ?: return
    showToolbar(columnIndex)
  }

  private fun actuallyPaintBars(graphics: Graphics2D, rect: Rectangle, hover: Boolean, accent: Boolean) {
    val paintCount = when {
      accent -> 2
      else -> 1
    }
    repeat(paintCount) {
      graphics.color = when {
        hover -> TableInlayProperties.barHoverColor
        else -> TableInlayProperties.barColor
      }
      graphics.fillRoundRect(rect.x, 0, rect.width, barHeight, barHeight, barHeight)
      graphics.clearOvalOverEditor(rect.x, 0, barHeight, barHeight)
      graphics.clearOvalOverEditor(rect.x + rect.width - barHeight, 0, barHeight, barHeight)
    }
  }

  private fun paintBars(graphics: Graphics2D) {
    val currentBarsModel = barsModel
    // First pass: paint each bar without circles
    for ((index, rect) in currentBarsModel.withIndex()) {
      val mouseIsOver = lastSelectedIndex == index
      actuallyPaintBars(graphics, rect, hover = mouseIsOver, accent = false)
    }
    // Second pass: paint each circle to fill up gaps
    repeat(2) {
      paintCircles(currentBarsModel) { x, _, _ ->
        graphics.color = TableInlayProperties.barColor
        graphics.fillOval(x, 0, barHeight, barHeight)
      }
    }
  }

  private fun paintCircles(rects: List<Rectangle>, width: Int = barHeight, block: (Int, Rectangle, Int) -> Unit) {
    if (rects.isNotEmpty()) {
      for ((index, rect) in rects.withIndex()) {
        block(rect.x, rect, index)
      }
      rects.last().let { block(it.x + it.width - width, it, -1) }
    }
  }

  companion object {
    private val columnActionGroup
      get() = ActionManager.getInstance().getAction("Markdown.TableColumnActions") as ActionGroup

    private val emptyBoundsState = BoundsState(0, 0, emptyList())

    // Should be even
    const val barHeight = TableInlayProperties.barSize
    const val leftPadding = VerticalBarPresentation.barWidth + TableInlayProperties.leftRightPadding * 2

    private fun wrapPresentation(factory: PresentationFactory, editor: Editor, presentation: InlayPresentation): InlayPresentation {
      return factory.inset(
        PresentationWithCustomCursor(editor, presentation),
        left = leftPadding,
        top = TableInlayProperties.topDownPadding,
        down = TableInlayProperties.topDownPadding
      )
    }

    fun create(factory: PresentationFactory, editor: Editor, table: MarkdownTable): InlayPresentation {
      return wrapPresentation(factory, editor, HorizontalBarPresentation(editor, table))
    }

    private fun obtainFontMetrics(editor: Editor): FontMetrics {
      val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
      return editor.contentComponent.getFontMetrics(font)
    }

    private fun uiDataSnapshot(sink: DataSink, table: MarkdownTable, columnIndex: Int) {
      val tableReference = WeakReference<PsiElement>(table)
      sink.lazy(TableActionKeys.COLUMN_INDEX) { columnIndex }
      sink.lazy(TableActionKeys.ELEMENT) { tableReference }
    }
  }
}
