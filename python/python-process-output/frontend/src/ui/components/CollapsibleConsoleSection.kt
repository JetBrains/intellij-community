package com.intellij.python.processOutput.frontend.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.python.processOutput.frontend.ConsoleTag
import com.intellij.python.processOutput.frontend.ConsoleTagFormatter
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.ui.ColorUtil
import com.intellij.ui.InplaceButton
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
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultCaret
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.Style
import javax.swing.text.StyleConstants

internal data class ConsoleTextLine<TTag>(
  val tag: TTag,
  val text: String,
  val foreground: Color? = null,
) where TTag : ConsoleTag, TTag : Enum<TTag>

internal class CollapsibleConsoleSection<TTag>(
  @Nls title: String,
  name: String,
  private val formatter: ConsoleTagFormatter<TTag>,
  private val onToggle: () -> Unit,
  private val onCopy: ((line: ConsoleTextLine<TTag>, index: Int) -> Unit)? = null,
) where TTag : ConsoleTag, TTag : Enum<TTag> {
  private var lines: List<ConsoleTextLine<TTag>> = emptyList()
  private var sections: List<Section<TTag>> = emptyList()
  private var showTags: Boolean = true
  private var wrap: Boolean = false
  private var expanded: Boolean = true

  private val textPane: JTextPane = 
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
  private val baseStyle: Style = textPane.addStyle(null, null)
  private val tagColumn = ColumnPanel { i -> sections.getOrNull(i)?.textOffset }
  private val copyColumn = ColumnPanel { i -> sections.getOrNull(i)?.textOffset }
  private val chevronLabel = JBLabel(AllIcons.General.ArrowDown)
  private val body: JPanel = JPanel(BorderLayout())
  private val header: JPanel = JPanel(BorderLayout())

  val component: JComponent
    field = JPanel(BorderLayout())

  init {
    textPane.isEditable = false
    textPane.isOpaque = false
    textPane.border = JBUI.Borders.empty()
    textPane.font = Styling.MONOSPACED_FONT
    textPane.caret.let { it as DefaultCaret }.updatePolicy = DefaultCaret.NEVER_UPDATE

    StyleConstants.setFontFamily(baseStyle, Styling.MONOSPACED_FONT.family)
    StyleConstants.setFontSize(baseStyle, Styling.MONOSPACED_FONT.size)

    body.isOpaque = false
    body.add(tagColumn, BorderLayout.WEST)
    body.add(textPane, BorderLayout.CENTER)
    body.add(copyColumn, BorderLayout.EAST)

    header.name = name
    header.isOpaque = false
    header.border = JBUI.Borders.empty(Styling.HEADER_VERTICAL_PADDING, Styling.HEADER_HORIZONTAL_PADDING)
    header.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

    chevronLabel.border = JBUI.Borders.emptyRight(Styling.HEADER_ICON_TEXT_GAP)

    header.add(chevronLabel, BorderLayout.WEST)
    header.add(JBLabel(title), BorderLayout.CENTER)

    header.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        onToggle()
      }
    })

    component.isOpaque = false
    component.add(header, BorderLayout.NORTH)
    component.add(body, BorderLayout.CENTER)

    applyExpanded()
    applyShowTags()
  }

  fun setLines(newLines: List<ConsoleTextLine<TTag>>) {
    lines = newLines

    rebuildText()
    rebuildSections()
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

    for ((_, text, foreground) in lines) {
      val attrs =
        if (foreground == null) {
          baseStyle
        }
        else {
          SimpleAttributeSet(baseStyle).also { StyleConstants.setForeground(it, foreground) }
        }

      doc.insertString(doc.length, text + "\n", attrs)
    }

    textPane.caretPosition = 0
  }

  private fun rebuildSections() {
    tagColumn.removeAll()
    copyColumn.removeAll()

    val newSections = mutableListOf<Section<TTag>>()
    var prevTag: TTag? = null
    var offset = 0

    for ((index, line) in lines.withIndex()) {
      if (line.tag != prevTag) {
        newSections += Section(line, offset, index)
      }

      prevTag = line.tag
      offset += line.text.length + 1 // trailing newline
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

      if (onCopy != null) {
        copyColumn.add(copyButton(section))
      }
    }

    body.revalidate()
    body.repaint()
  }

  private fun copyButton(section: Section<TTag>): JComponent {
    val iconButton = IconButton(message("process.output.output.copySection.tooltip"), AllIcons.Actions.Copy)
    val button = InplaceButton(iconButton) {
      onCopy?.invoke(section.line, section.index)
    }

    button.preferredSize = Dimension(Styling.COPY_BUTTON_SIZE, Styling.COPY_BUTTON_SIZE)

    return button
  }

  private inner class ColumnPanel(private val childOffset: (Int) -> Int?) : JPanel(null) {
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

  private data class Section<TTag>(
    val line: ConsoleTextLine<TTag>,
    val textOffset: Int,
    val index: Int,
  ) where TTag : ConsoleTag, TTag : Enum<TTag>

  private object Styling {
    const val COPY_BUTTON_SIZE = 18
    const val HEADER_VERTICAL_PADDING = 4
    const val HEADER_HORIZONTAL_PADDING = 8
    const val HEADER_ICON_TEXT_GAP = 4
    val TAG_FOREGROUND = ColorUtil.withAlpha(JBUI.CurrentTheme.Label.foreground(), 0.75)

    val MONOSPACED_FONT =
      EditorColorsManager.getInstance().globalScheme.let {
        Font(it.editorFontName, Font.PLAIN, it.editorFontSize)
      }
  }
}
