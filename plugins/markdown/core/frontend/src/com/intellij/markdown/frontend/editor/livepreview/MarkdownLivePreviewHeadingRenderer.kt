// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorCssFontResolver
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.ui.AppUIUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.toTextRange
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import javax.swing.SwingUtilities
import javax.swing.text.AttributeSet
import javax.swing.text.Element
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument

private val HeadingKeys = listOf(
  MarkdownHighlighterColors.HEADER_LEVEL_1,
  MarkdownHighlighterColors.HEADER_LEVEL_2,
  MarkdownHighlighterColors.HEADER_LEVEL_3,
  MarkdownHighlighterColors.HEADER_LEVEL_4,
  MarkdownHighlighterColors.HEADER_LEVEL_5,
  MarkdownHighlighterColors.HEADER_LEVEL_6,
)

/** [JBHtmlPane] represents a `<wbr>` with this character. */
private const val ZERO_WIDTH_SPACE = '\u200B'

/** The modifier keys of a mouse event, without the mouse buttons. */
private const val MODIFIER_KEYS = InputEvent.SHIFT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or
  InputEvent.META_DOWN_MASK or InputEvent.ALT_GRAPH_DOWN_MASK

/** The attribute that the backend puts on the heading HTML: the `start..end` source range, relative to the heading line start. */
private const val SOURCE_RANGE_ATTRIBUTE = "md-src-pos"

/**
 * Paints each heading line as HTML in a custom fold. The HTML heading element supplies its font size.
 * While a heading shows its source, a block inlay below it takes the height difference to prevent UI jumps.
 * A plain click on a painted heading moves the caret to the source character under the mouse.
 * The Go-to Declaration mouse shortcut, such as Ctrl+Click, also runs Go to Declaration there.
 */
internal class MarkdownLivePreviewHeadingRenderer(private val editor: EditorEx) : MarkdownLivePreviewElementRenderer {
  private val spacers = ArrayList<Inlay<HeadingSpacer>>()

  init {
    editor.addEditorMouseListener(object : EditorMouseListener {
      override fun mousePressed(event: EditorMouseEvent) = moveCaretToClickedSource(event)
    }, this)
  }

  override fun presentation(spec: MarkdownLivePreviewSpec): List<MarkdownLivePreviewFold> =
    listOf(HeadingFold(spec as MarkdownLivePreviewSpec.Heading))

  override fun documentChanged() = Unit

  override fun reconcile(presentation: MarkdownLivePreviewPresentation?) {
    val wanted = HashMap<Int, Int>()
    for (element in presentation?.elements.orEmpty()) {
      val fold = element.folds.singleOrNull() as? HeadingFold ?: continue
      val range = fold.range
      val region = editor.foldingModel.getFoldRegion(range.startOffset, range.endOffset)
      if (region is CustomFoldRegion) {
        if ((region.renderer as? HeadingPainter)?.needsLayout() == true) region.update()
        continue
      }
      val sourceLines = editor.offsetToVisualLine(range.endOffset, true) - editor.offsetToVisualLine(range.startOffset, false) + 1
      val height = fold.painter().height() - sourceLines * editor.lineHeight
      if (height > 0) wanted[range.endOffset] = height
    }
    spacers.removeIf { spacer ->
      val keep = spacer.isValid && wanted[spacer.offset] == spacer.renderer.height
      if (keep) wanted.remove(spacer.offset) else Disposer.dispose(spacer)
      !keep
    }
    // The spacer takes the place of the painted heading, so it sits closer to the line than other inlays, such as an image.
    val properties = InlayProperties().relatesToPrecedingText(true).priority(Int.MAX_VALUE)
    for ((offset, height) in wanted) {
      editor.inlayModel.addBlockElement(offset, properties, HeadingSpacer(height))?.let(spacers::add)
    }
  }

  override fun dispose() {
    spacers.forEach(Disposer::dispose)
    spacers.clear()
  }

  private fun moveCaretToClickedSource(event: EditorMouseEvent) {
    val mouse = event.mouseEvent
    if (event.isConsumed || !SwingUtilities.isLeftMouseButton(mouse) || mouse.clickCount != 1) return
    val modifiers = mouse.modifiersEx and MODIFIER_KEYS
    val goToDeclaration = modifiers != 0 && KeymapManager.getInstance()?.activeKeymap?.let {
      KeymapUtil.matchActionMouseShortcutsModifiers(it, modifiers, IdeActions.ACTION_GOTO_DECLARATION)
    } == true
    if (modifiers != 0 && !goToDeclaration) return
    val region = event.collapsedFoldRegion as? CustomFoldRegion ?: return
    val painter = region.renderer as? HeadingPainter ?: return
    val location = region.location ?: return
    val offset = painter.sourceOffsetAt(region, Point(mouse.x - location.x, mouse.y - location.y)) ?: return
    event.consume()
    editor.caretModel.removeSecondaryCarets()
    editor.selectionModel.removeSelection()
    editor.caretModel.moveToOffset(region.startOffset + offset)
    if (goToDeclaration) goToDeclarationAtCaret(mouse)
  }

  /**
   * Runs Go to Declaration at the caret, as the mouse shortcut does on the source.
   * The heading now shows its source, so other text is under the mouse. The release of this click must not run the action again.
   */
  private fun goToDeclarationAtCaret(mouse: MouseEvent) {
    IdeEventQueue.getInstance().blockNextEvents(mouse, IdeEventQueue.BlockMode.ACTIONS)
    val action = ActionManager.getInstance().getAction(IdeActions.ACTION_GOTO_DECLARATION) ?: return
    ApplicationManager.getApplication().invokeLater({
      if (editor.isDisposed) return@invokeLater
      val dataContext = EditorUtil.getEditorDataContext(editor)
      ActionUtil.performAction(action, AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.MOUSE_SHORTCUT, ActionUiKind.NONE, null))
    }, ModalityState.stateForComponent(editor.contentComponent))
  }

  private inner class HeadingFold(private val heading: MarkdownLivePreviewSpec.Heading) : MarkdownLivePreviewFold {
    override val range: TextRange = heading.range.toTextRange()
    private var painter: HeadingPainter? = null

    override fun isSame(region: FoldRegion): Boolean = ((region as? CustomFoldRegion)?.renderer as? HeadingPainter)?.look == look()

    override fun create(editor: EditorEx): FoldRegion? {
      val foldingModel = editor.foldingModel
      // The folding model refuses a custom fold that shares a boundary with a larger fold, such as the section fold that
      // starts at a heading. An expanded fold hides nothing, so the heading replaces it.
      for (region in foldingModel.getRegionsOverlappingWith(range.startOffset, range.endOffset)) {
        if (region !is CustomFoldRegion && region.isExpanded && !range.contains(region.textRange) &&
            (region.startOffset == range.startOffset || region.endOffset == range.endOffset)) {
          foldingModel.removeFoldRegion(region)
        }
      }
      val line = editor.document.getLineNumber(range.startOffset)
      return foldingModel.addCustomLinesFolding(line, line, painter())
    }

    /** The painter for the current look. A reveal and a refold in one presentation reuse it. */
    fun painter(): HeadingPainter {
      val look = look()
      return painter?.takeIf { it.look == look } ?: HeadingPainter(editor, look).also { painter = it }
    }

    private fun look(): HeadingLook {
      val scheme = editor.colorsScheme
      val font = scheme.getFont(EditorFontType.PLAIN)
      val attributes = scheme.getAttributes(HeadingKeys[heading.level - 1])
      return HeadingLook(
        html = "<h${heading.level}>${heading.html}</h${heading.level}>",
        font = font.deriveFont(attributes?.fontType ?: Font.BOLD),
        foreground = attributes?.foregroundColor ?: scheme.defaultForeground,
        linkColor = scheme.getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES)?.foregroundColor ?: scheme.defaultForeground,
      )
    }
  }
}

/** Everything a heading pane depends on. Equal looks paint the same pixels, so the reconciler keeps their regions. */
private data class HeadingLook(val html: String, val font: Font, val foreground: Color, val linkColor: Color)

/** Paints one heading with its own [JBHtmlPane]. The layout is cached for the current wrap width. */
private class HeadingPainter(private val editor: EditorEx, val look: HeadingLook) : CustomFoldRegionRenderer {
  private val pane = createPane()
  private var laidOut = false
  private var layoutWidth: Int? = null

  fun needsLayout(): Boolean = !laidOut || layoutWidth != wrapWidth(editor)

  fun height(): Int = maxOf(editor.lineHeight, layout().height)

  override fun calcWidthInPixels(region: CustomFoldRegion): Int = layout().width

  override fun calcHeightInPixels(region: CustomFoldRegion): Int = height()

  override fun paint(region: CustomFoldRegion, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
    val graphics = g.create() as Graphics2D
    try {
      graphics.translate(targetRegion.x, targetRegion.y + paneY(targetRegion.height.toInt()))
      UISettings.setupAntialiasing(graphics)
      pane.paint(graphics)
    }
    finally {
      graphics.dispose()
    }
  }

  /**
   * Maps [point], relative to [region], to the source offset of the text under it, relative to the heading line start.
   * Returns null when the point has no source range, and the platform then handles the click.
   */
  fun sourceOffsetAt(region: CustomFoldRegion, point: Point): Int? {
    val document = pane.document as? HTMLDocument ?: return null
    val position = pane.viewToModel2D(Point(point.x, point.y - paneY(region.heightInPixels)))
    if (position < 0) return null
    // A point after the last character of a run lands on the next element, so it falls back to the run before it.
    val element = document.getCharacterElement(position).takeIf { it.sourceKey() != null }
                  ?: document.getCharacterElement(maxOf(0, position - 1))
    val key = element.sourceKey() ?: return null
    val range = (element.attributes.getAttribute(key) as AttributeSet).sourceRange() ?: return null
    val run = document.sharedRun(element, key)
    val index = document.getText(run.startOffset, position - run.startOffset).count { it != ZERO_WIDTH_SPACE }
    if (key == HTML.Tag.SPAN) return (range.first + index).coerceAtMost(range.last)
    // A tag without text spans, such as a code span, shows a part of its source. Find that part to map the index.
    val text = document.getText(run.startOffset, run.length).filter { it != ZERO_WIDTH_SPACE }
    val sourceText = editor.document.charsSequence
    val source = sourceText.subSequence(region.startOffset + range.first, minOf(region.startOffset + range.last, sourceText.length))
    return (range.first + source.indexOf(text).coerceAtLeast(0) + index).coerceAtMost(range.last)
  }

  private fun layout(): Dimension {
    if (needsLayout()) {
      val width = wrapWidth(editor)
      pane.setSize(width ?: 10_000_000, 10_000_000)
      val preferred = pane.preferredSize
      pane.setSize(width ?: preferred.width.coerceAtLeast(1), preferred.height)
      layoutWidth = width
      laidOut = true
    }
    return pane.size
  }

  private fun paneY(regionHeight: Int): Int = ((regionHeight - pane.height) / 2).coerceAtLeast(0)

  private fun createPane(): JBHtmlPane {
    val weight = if (look.font.isBold) "bold" else "normal"
    val style = if (look.font.isItalic) "italic" else "normal"
    val css = "body { margin: 0; padding: 0; font-family: ${EditorCssFontResolver.EDITOR_FONT_NAME_PLACEHOLDER}; " +
              "font-weight: $weight; font-style: $style } " +
              "h1, h2, h3, h4, h5, h6 { margin: 0; padding: 0 } " +
              "a { color: #${ColorUtil.toHex(look.linkColor)}; text-decoration: underline } " +
              ".user-del { text-decoration: line-through }"
    return JBHtmlPane(
      JBHtmlPaneStyleConfiguration {
        colorSchemeProvider = { editor.colorsScheme }
        editorInlineContext = true
        spaceBeforeParagraph = 0
        spaceAfterParagraph = 0
      },
      JBHtmlPaneConfiguration.builder()
        .fontResolver(EditorCssFontResolver.getInstance(editor))
        .customStyleSheet(css)
        .build(),
    ).apply {
      border = JBUI.Borders.empty()
      isOpaque = false
      isFocusable = false
      font = look.font
      foreground = look.foreground
      UIUtil.enableEagerSoftWrapping(this)
      text = look.html
      AppUIUtil.targetToDevice(this, editor.contentComponent)
    }
  }
}

/** The width that soft wraps use, or null when the editor does not soft-wrap. */
private fun wrapWidth(editor: EditorEx): Int? {
  if (!editor.softWrapModel.isSoftWrappingEnabled) return null
  val insets = editor.contentComponent.insets
  return (editor.scrollingModel.visibleArea.width - insets.left - insets.right).takeIf { it > 0 }
}

/**
 * The attribute key of the source range for this text: the innermost text span, or else an inline tag with a source range,
 * such as `<code>`.
 */
private fun Element.sourceKey(): Any? {
  if ((attributes.getAttribute(HTML.Tag.SPAN) as? AttributeSet)?.sourceRange() != null) return HTML.Tag.SPAN
  return attributes.attributeNames.toList().firstOrNull { (attributes.getAttribute(it) as? AttributeSet)?.sourceRange() != null }
}

/** The leaves around [element] that share its attribute value for [key]. A `<wbr>` can split one run into several leaves. */
private fun HTMLDocument.sharedRun(element: Element, key: Any): TextRange {
  val value = element.attributes.getAttribute(key)
  var start = element.startOffset
  while (start > 0 && getCharacterElement(start - 1).attributes.getAttribute(key) === value) {
    start = getCharacterElement(start - 1).startOffset
  }
  var end = element.endOffset
  while (end < length && getCharacterElement(end).attributes.getAttribute(key) === value) {
    end = getCharacterElement(end).endOffset
  }
  return TextRange(start, end)
}

/** Parses the `md-src-pos` value `start..end`. */
private fun AttributeSet.sourceRange(): IntRange? {
  val value = getAttribute(SOURCE_RANGE_ATTRIBUTE) as? String ?: return null
  val start = value.substringBefore("..").toIntOrNull() ?: return null
  val end = value.substringAfter("..").toIntOrNull() ?: return null
  return start..end
}

private class HeadingSpacer(val height: Int) : EditorCustomElementRenderer {
  override fun calcWidthInPixels(inlay: Inlay<*>): Int = 0
  override fun calcHeightInPixels(inlay: Inlay<*>): Int = height
}
