/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package training.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.util.*
import javax.swing.JTextPane
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * Created by karashevich on 01/09/15.
 */

class LessonMessagePane : JTextPane() {

  private val lessonMessages = ArrayList<LessonMessage>()

  private var fontFamily: String? = null

  //, fontFace, check_width + check_right_indent
  init {
    initStyleConstants()
    isEditable = false
  }

  private fun initStyleConstants() {
    fontFamily = Font(LearnUIManager.getInstance().fontFace, Font.PLAIN, LearnUIManager.getInstance().fontSize).family
    font = Font(LearnUIManager.getInstance().fontFace, Font.PLAIN, LearnUIManager.getInstance().fontSize)

    StyleConstants.setFontFamily(REGULAR, fontFamily)
    StyleConstants.setFontSize(REGULAR, LearnUIManager.getInstance().fontSize)
    StyleConstants.setForeground(REGULAR, JBColor.BLACK)

    StyleConstants.setFontFamily(BOLD, fontFamily)
    StyleConstants.setFontSize(BOLD, LearnUIManager.getInstance().fontSize)
    StyleConstants.setBold(BOLD, true)
    StyleConstants.setForeground(BOLD, JBColor.BLACK)

    StyleConstants.setFontFamily(SHORTCUT, fontFamily)
    StyleConstants.setFontSize(SHORTCUT, LearnUIManager.getInstance().fontSize)
    StyleConstants.setBold(SHORTCUT, true)
    StyleConstants.setForeground(SHORTCUT, JBColor.BLACK)

    StyleConstants.setForeground(CODE, JBColor.BLUE)
    EditorColorsManager.getInstance().globalScheme.editorFontName
    StyleConstants.setFontFamily(CODE, EditorColorsManager.getInstance().globalScheme.editorFontName)
    StyleConstants.setFontSize(CODE, LearnUIManager.getInstance().fontSize)

    StyleConstants.setForeground(LINK, JBColor.BLUE)
    StyleConstants.setFontFamily(LINK, fontFamily)
    StyleConstants.setUnderline(LINK, true)
    StyleConstants.setFontSize(LINK, LearnUIManager.getInstance().fontSize)

    StyleConstants.setLeftIndent(PARAGRAPH_STYLE, LearnUIManager.getInstance().checkIndent.toFloat())
    StyleConstants.setRightIndent(PARAGRAPH_STYLE, 0f)
    StyleConstants.setSpaceAbove(PARAGRAPH_STYLE, 16.0f)
    StyleConstants.setSpaceBelow(PARAGRAPH_STYLE, 0.0f)
    StyleConstants.setLineSpacing(PARAGRAPH_STYLE, 0.2f)

    StyleConstants.setForeground(REGULAR, LearnUIManager.getInstance().defaultTextColor)
    StyleConstants.setForeground(BOLD, LearnUIManager.getInstance().defaultTextColor)
    StyleConstants.setForeground(SHORTCUT, LearnUIManager.getInstance().shortcutTextColor)
    StyleConstants.setForeground(LINK, LearnUIManager.getInstance().lessonLinkColor)
    StyleConstants.setForeground(CODE, LearnUIManager.getInstance().lessonLinkColor)

    this.setParagraphAttributes(PARAGRAPH_STYLE, true)
  }

  fun addMessage(text: String) {
    try {
      val start = document.length
      document.insertString(document.length, text, REGULAR)
      val end = document.length
      lessonMessages.add(LessonMessage(text, start, end))

    }
    catch (e: BadLocationException) {
      e.printStackTrace()
    }

  }

  fun addMessage(messages: Array<Message>) {
    try {
      val start = document.length
      if (lessonMessages.isNotEmpty())
        document.insertString(document.length, "\n", REGULAR)
      for (message in messages) {
        val startOffset = document.endPosition.offset
        message.startOffset = startOffset
        when (message.type) {
          Message.MessageType.TEXT_REGULAR -> document.insertString(document.length, message.text, REGULAR)
          Message.MessageType.TEXT_BOLD -> document.insertString(document.length, message.text, BOLD)
          Message.MessageType.SHORTCUT -> document.insertString(document.length, " ${message.text} ", SHORTCUT)
          Message.MessageType.CODE -> document.insertString(document.length, message.text, CODE)
          Message.MessageType.CHECK -> document.insertString(document.length, message.text, ROBOTO)
          Message.MessageType.LINK -> appendLink(message)
        }
        message.endOffset = document.endPosition.offset
      }
      val end = document.length
      lessonMessages.add(LessonMessage(messages, start, end))
    } catch (e: BadLocationException) {
      LOG.warn(e)
    }
  }

  /**
   * inserts a checkmark icon to the end of the LessonMessagePane document as a styled label.
   */
  @Throws(BadLocationException::class)
  fun passPreviousMessages() {
    if (lessonMessages.size > 0) {
      val lessonMessage = lessonMessages[lessonMessages.size - 1]
      lessonMessage.isPassed = true

      //Repaint text with passed style
      val passedStyle = this.addStyle("PassedStyle", null)
      StyleConstants.setForeground(passedStyle, LearnUIManager.getInstance().passedColor)
      styledDocument.setCharacterAttributes(0, lessonMessage.end, passedStyle, false)
    }
  }

  fun clear() {
    text = ""
    lessonMessages.clear()
  }

  /**
   * Appends link inside JTextPane to Run another lesson

   * @param message - should have LINK type. message.runnable starts when the message has been clicked.
   */
  @Throws(BadLocationException::class)
  private fun appendLink(message: Message) {
    val startLink = document.endPosition.offset
    document.insertString(document.length, message.text, LINK)
    val endLink = document.endPosition.offset

    addMouseListener(object : MouseAdapter() {

      override fun mouseClicked(me: MouseEvent?) {
        val x = me!!.x
        val y = me.y

        val clickOffset = viewToModel(Point(x, y))
        if (clickOffset in startLink..endLink && message.runnable != null) {
          message.runnable!!.run()
        }

      }
    })
  }

  override fun paintComponent(g: Graphics) {
    try {
      paintShortcutBackground(g)
    }
    catch (e: BadLocationException) {
      e.printStackTrace()
    }

    super.paintComponent(g)
    paintLessonCheckmarks(g)
  }

  private fun paintLessonCheckmarks(g: Graphics) {
    for (lessonMessage in lessonMessages) {
      if (lessonMessage.isPassed) {
        var startOffset = lessonMessage.start
        if (startOffset != 0) startOffset++
        try {
          val rectangle = modelToView(startOffset)
          if (!SystemInfo.isMac) {
            LearnIcons.checkMarkGray.paintIcon(this, g, rectangle.x - 17, rectangle.y + 3)
          }
          else {
            LearnIcons.checkMarkGray.paintIcon(this, g, rectangle.x - 17, rectangle.y + 1)
          }
        }
        catch (e: BadLocationException) {
          e.printStackTrace()
        }

      }
    }
  }

  @Throws(BadLocationException::class)
  private fun paintShortcutBackground(g: Graphics) {
    val g2d = g as Graphics2D
    for (lessonMessage in lessonMessages) {
      val myMessages = lessonMessage.myMessages
      for (myMessage in myMessages) {
        if (myMessage.type == Message.MessageType.SHORTCUT) {
          val startOffset = myMessage.startOffset
          val endOffset = myMessage.endOffset
          val rectangleStart = modelToView(startOffset)
          val rectangleEnd = modelToView(endOffset - 2)
          val color = g2d.color
          val fontSize = LearnUIManager.getInstance().fontSize

          g2d.color = LearnUIManager.getInstance().shortcutBackgroundColor
          val r2d: RoundRectangle2D
          if (!SystemInfo.isMac)
            r2d = RoundRectangle2D.Double(rectangleStart.getX() - 2 * indent, rectangleStart.getY() - indent + 1,
                                          rectangleEnd.getX() - rectangleStart.getX() + 4 * indent, (fontSize + 3 * indent).toDouble(),
                                          arc.toDouble(), arc.toDouble())
          else
            r2d = RoundRectangle2D.Double(rectangleStart.getX() - 2 * indent, rectangleStart.getY() - indent,
                                          rectangleEnd.getX() - rectangleStart.getX() + 4 * indent, (fontSize + 3 * indent).toDouble(),
                                          arc.toDouble(), arc.toDouble())
          g2d.fill(r2d)
          g2d.color = color
        }
      }
    }
  }

  companion object {

    private val LOG = Logger.getInstance(LessonMessagePane::class.java.canonicalName)

    //Style Attributes for LessonMessagePane(JTextPane)
    private val REGULAR = SimpleAttributeSet()
    private val BOLD = SimpleAttributeSet()
    private val SHORTCUT = SimpleAttributeSet()
    private val ROBOTO = SimpleAttributeSet()
    private val CODE = SimpleAttributeSet()
    private val LINK = SimpleAttributeSet()
    private val PARAGRAPH_STYLE = SimpleAttributeSet()

    //arc & indent for shortcut back plate
    private val arc = 4
    private val indent = 2
  }


}

