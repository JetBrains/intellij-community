package com.intellij.python.processOutput.frontend.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.python.processOutput.frontend.ConsoleTag
import com.intellij.python.processOutput.frontend.ConsoleTagFormatter
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.SizeRequirements
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.text.AbstractDocument
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultCaret
import javax.swing.text.Element
import javax.swing.text.LabelView
import javax.swing.text.ParagraphView
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.Style
import javax.swing.text.StyleConstants
import javax.swing.text.StyledEditorKit
import javax.swing.text.View
import javax.swing.text.ViewFactory

internal data class ConsoleTextLine<TTag>(
  val tag: TTag,
  val text: String,
  val foreground: Color? = null,
) where TTag : ConsoleTag, TTag : Enum<TTag>

internal class ConsoleRegion<TTag> private constructor(
  @Nls title: String,
  name: String,
  private val formatter: ConsoleTagFormatter<TTag>,
  private val onChevronClicked: (() -> Unit)? = null,
  private val onRebuild: (() -> Unit)? = null,
) where TTag : ConsoleTag, TTag : Enum<TTag> {
  private var lines = emptyList<ConsoleTextLine<TTag>>()
  private var sections = emptyList<Section<TTag>>()
  private var showTags = true
  private var wrap = false
  private var expanded = true

  private val textPane =
    object : JTextPane() {
      override fun getPreferredSize(): Dimension {
        val superSize = super.getPreferredSize()

        if (wrap) {
          return superSize
        }

        val fm = getFontMetrics(font)
        val natural = lines.maxOfOrNull { fm.stringWidth(it.text) } ?: 0
        val insets = insets
        val width = natural + insets.left + insets.right

        return Dimension(maxOf(superSize.width, width), superSize.height)
      }
    }
  private var baseStyle: Style
  private val tagColumn = ColumnPanel { i -> sections.getOrNull(i)?.textOffset }
  private val chevronLabel = JBLabel(AllIcons.General.ArrowDown)
  private val body = JPanel(BorderLayout())
  private val header = JPanel(BorderLayout())
  private val isCollapsible = onChevronClicked != null

  val component: JComponent
    field = JPanel(BorderLayout())

  init {
    textPane.editorKit = WrappingEditorKit()
    baseStyle = textPane.addStyle(null, null)

    textPane.isEditable = false
    textPane.isOpaque = false
    textPane.border = JBUI.Borders.empty()
    textPane.font = Styling.MONOSPACED_FONT
    textPane.caret.let { it as DefaultCaret }.updatePolicy = DefaultCaret.NEVER_UPDATE

    StyleConstants.setFontFamily(baseStyle, Styling.MONOSPACED_FONT.family)
    StyleConstants.setFontSize(baseStyle, Styling.MONOSPACED_FONT.size)

    body.isOpaque = false
    body.border =
      if (isCollapsible) {
        JBUI.Borders.emptyBottom(Styling.BODY_BOTTOM_PADDING)
      }
      else {
        body.border
      }
    body.add(tagColumn, BorderLayout.WEST)
    body.add(textPane, BorderLayout.CENTER)

    header.name = name
    header.isOpaque = false
    header.border = JBUI.Borders.empty(Styling.HEADER_VERTICAL_PADDING, Styling.HEADER_HORIZONTAL_PADDING)
    header.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

    chevronLabel.border = JBUI.Borders.emptyRight(Styling.HEADER_ICON_TEXT_GAP)

    header.add(chevronLabel, BorderLayout.WEST)
    header.add(JBLabel(title), BorderLayout.CENTER)

    header.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        onChevronClicked?.invoke()
      }
    })

    if (!isCollapsible) {
      header.isVisible = false
    }

    component.isOpaque = false
    component.add(header, BorderLayout.NORTH)
    component.add(body, BorderLayout.CENTER)

    applyExpanded()
    applyShowTags()
  }

  fun setLines(newLines: List<ConsoleTextLine<TTag>>) {
    lines = newLines

    if (expanded) {
      rebuildText()
      rebuildSections()
    }
  }

  fun setShowTags(show: Boolean) {
    if (showTags == show) {
      return
    }

    showTags = show

    applyShowTags()
  }

  fun setWrapContent(newWrap: Boolean) {
    if (wrap == newWrap) {
      return
    }

    wrap = newWrap

    SwingUtilities.invokeLater {
      textPane.revalidate()
      body.revalidate()
      body.repaint()
    }
  }

  fun setExpanded(newExpanded: Boolean) {
    if (expanded == newExpanded) {
      return
    }

    expanded = newExpanded

    applyExpanded()

    if (newExpanded) {
      SwingUtilities.invokeLater {
        rebuildText()
        rebuildSections()
      }
    }
  }

  private fun applyExpanded() {
    body.isVisible = expanded
    chevronLabel.icon =
      if (expanded) {
        AllIcons.General.ArrowDown
      }
      else {
        AllIcons.General.ArrowRight
      }

    SwingUtilities.invokeLater {
      component.revalidate()
      component.repaint()
    }
  }

  private fun applyShowTags() {
    tagColumn.isVisible = showTags

    body.revalidate()
    body.repaint()
  }

  private fun rebuildText() {
    val doc = textPane.styledDocument

    doc.remove(0, doc.length)

    for ((index, line) in lines.withIndex()) {
      val (_, text, foreground) = line
      val attrs =
        if (foreground == null) {
          baseStyle
        }
        else {
          SimpleAttributeSet(baseStyle).also { StyleConstants.setForeground(it, foreground) }
        }
      var finalText = text

      if (index < lines.size - 1) {
        finalText += "\n"
      }

      doc.insertString(doc.length, finalText, attrs)
    }

    textPane.caretPosition = 0
  }

  private fun rebuildSections() {
    tagColumn.removeAll()

    val newSections = mutableListOf<Section<TTag>>()
    var prevTag: TTag? = null
    var offset = 0

    for ((index, line) in lines.withIndex()) {
      if (line.tag != prevTag) {
        newSections += Section(line, offset, index)
      }

      prevTag = line.tag
      offset += line.text.length

      if (index < lines.size - 1) {
        offset += 1 // trailing newline
      }
    }

    sections = newSections

    for (section in sections) {
      val tagText = formatter.colonTagString(section.line.tag)
      val tagLabel = JBLabel(tagText)

      tagLabel.font = textPane.font
      tagLabel.foreground = Styling.TAG_FOREGROUND
      tagLabel.horizontalAlignment = SwingConstants.RIGHT
      tagLabel.verticalAlignment = SwingConstants.TOP
      tagColumn.add(tagLabel)
    }

    SwingUtilities.invokeLater {
      body.revalidate()
      body.repaint()

      onRebuild?.invoke()
    }
  }

  private inner class ColumnPanel(private val childOffset: (Int) -> Int?) : JComponent() {
    init {
      isOpaque = false
    }

    override fun doLayout() {
      for (i in 0..<componentCount) {
        val child = getComponent(i)
        val offset = childOffset(i) ?: continue
        val y = offsetToY(offset)
        val childPref = child.preferredSize
        val childHeight = childPref.height
        val availableWidth = width.coerceAtLeast(childPref.width)

        child.setBounds(0, y, availableWidth, childHeight)
      }
    }

    override fun getPreferredSize(): Dimension {
      var maxWidth = 0

      for (i in 0..<componentCount) {
        val childPrefWidth = getComponent(i).preferredSize.width

        if (childPrefWidth > maxWidth) {
          maxWidth = childPrefWidth
        }
      }

      return Dimension(maxWidth, textPane.preferredSize.height)
    }

    private fun offsetToY(offset: Int): Int =
      try {
        val rect = textPane.modelToView2D(offset) ?: return 0

        rect.y.toInt()
      }
      catch (_: BadLocationException) {
        0
      }
  }

  private class WrappingEditorKit : StyledEditorKit() {
    private val delegate = super.getViewFactory()
    private val factory = ViewFactory { elem -> createView(elem) }

    override fun getViewFactory(): ViewFactory = factory

    private fun createView(elem: Element): View =
      when (elem.name) {
        AbstractDocument.ParagraphElementName ->
          object : ParagraphView(elem) {
            override fun calculateMinorAxisRequirements(axis: Int, r: SizeRequirements?): SizeRequirements =
              super.calculateMinorAxisRequirements(axis, r).also { it.minimum = 0 }
          }
        AbstractDocument.ContentElementName ->
          object : LabelView(elem) {
            override fun getMinimumSpan(axis: Int): Float =
              if (axis == X_AXIS) {
                0f
              }
              else {
                super.getMinimumSpan(axis)
              }

            override fun getBreakWeight(axis: Int, pos: Float, len: Float): Int {
              if (axis != X_AXIS) {
                return super.getBreakWeight(axis, pos, len)
              }

              val superWeight = super.getBreakWeight(axis, pos, len)

              return if (superWeight < GoodBreakWeight) {
                GoodBreakWeight
              }
              else {
                superWeight
              }
            }
          }
        else -> delegate.create(elem)
      }
  }

  private data class Section<TTag>(
    val line: ConsoleTextLine<TTag>,
    val textOffset: Int,
    val index: Int,
  ) where TTag : ConsoleTag, TTag : Enum<TTag>

  private object Styling {
    const val HEADER_VERTICAL_PADDING = 4
    const val HEADER_HORIZONTAL_PADDING = 8
    const val HEADER_ICON_TEXT_GAP = 4
    const val BODY_BOTTOM_PADDING = 8
    val TAG_FOREGROUND = ColorUtil.withAlpha(JBUI.CurrentTheme.Label.foreground(), 0.75)

    val MONOSPACED_FONT =
      EditorColorsManager.getInstance().globalScheme.let {
        Font(it.editorFontName, Font.PLAIN, it.editorFontSize)
      }
  }

  companion object {
    fun <TTag> createCollapsibleRegion(
      @Nls title: String,
      name: String,
      formatter: ConsoleTagFormatter<TTag>,
      onChevronClicked: () -> Unit,
      onRebuild: (() -> Unit)? = null,
    ) where TTag : ConsoleTag, TTag : Enum<TTag> =
      ConsoleRegion(title, name, formatter, onChevronClicked, onRebuild)

    fun <TTag> createStaticRegion(
      name: String,
      formatter: ConsoleTagFormatter<TTag>,
      onRebuild: (() -> Unit)? = null,
    ) where TTag : ConsoleTag, TTag : Enum<TTag> =
      ConsoleRegion("", name, formatter, null, onRebuild)
  }
}
