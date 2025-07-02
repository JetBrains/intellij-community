package com.intellij.mcpserver.util

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.Stack
import java.io.StringReader
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

// File copied from plugins/llm/core/src/com/intellij/ml/llm/core/chat/ui/chat/HtmlToMarkdownConverter.kt

fun convertHtmlToMarkdown(html: String): String = HtmlToMarkdownConverter().convert(html)

private const val HTML_TAG_LATEX_ATTRIBUTE_TEXT = "latex_text"
private const val HTML_TAG_LATEX = "latex_unknown_tag"

/** The `user-del` CSS cass is used by `MarkdownToHtmlConverterKt.convertMarkdownToHtml` as
    an alternative to `S/STRIKE` tags while MD->HTML conversion. */
private const val CLASS_STRIKE = "user-del"

private class HtmlToMarkdownConverter : HTMLEditorKit.ParserCallback() {
  private val markdownBuilder = StringBuilder()
  private var listStack = Stack<Int>()
  private var inSaveSpaces = 0
  private var inBlockquote = 0
  private var rowCount = 0
  private var trimNextTextIndent = false
  private var cellCountInRow = 0
  private var href: String = ""
  private var lineEnd: String = "\n"
  private var endTerminators = Stack<Pair<HTML.Tag, String>>()

  fun convert(html: String): String {
    val reader = StringReader(html)
    val parserDelegator = ParserDelegator()
    parserDelegator.parse(reader, this, true)
    return markdownBuilder.toString().trim()
  }

  override fun handleText(data: CharArray, pos: Int) {
    var text = String(data)
    if (inSaveSpaces <= 0) text = text.replace("\\s+".toRegex(), " ")
    if (trimNextTextIndent) {
      text = text.trimIndent()
      trimNextTextIndent = false
    }
    markdownBuilder.append(text)
  }

  fun generateLineEnd() {
    val b = StringBuilder("\n")
    repeat(inBlockquote) { b.append("> ") }
    lineEnd = b.toString()
  }

  fun addNewLine() {
    markdownBuilder.append(lineEnd)
  }

  fun addNewLineIfNeeded() {
    if (markdownBuilder.isNotEmpty() && !markdownBuilder.toString().endsWith(lineEnd)) {
      addNewLine()
    }
  }

  fun addLineSeparator() {
    if (markdownBuilder.isNotEmpty() && !markdownBuilder.toString().endsWith(lineEnd + lineEnd)) {
      markdownBuilder.append(lineEnd)
    }
  }

  fun addSpaceIfNeeded() {
    if (markdownBuilder.isNotEmpty() && markdownBuilder.last() != ' ') {
      markdownBuilder.append(' ')
    }
  }

  override fun handleSimpleTag(t: HTML.Tag?, a: MutableAttributeSet, pos: Int) {
    when (t) {
      HTML.Tag.BR -> addNewLine()
      HTML.Tag.IMG -> {
        val src = a.getAttribute(HTML.Attribute.SRC) as? String
        val alt = a.getAttribute(HTML.Attribute.ALT) as? String ?: ""
        if (src != null) {
          markdownBuilder.append("![$alt]($src)")
        }
      }
      HTML.Tag.INPUT -> {
        val inputType = a.getAttribute(HTML.Attribute.TYPE) as? String
        if (StringUtil.equalsIgnoreCase(inputType, "checkbox")) {
          val isChecked = a.getAttribute(HTML.Attribute.CHECKED) != null
          addSpaceIfNeeded()
          markdownBuilder.append("[${if (isChecked) "x" else " "}] ")
          trimNextTextIndent = true
        }
      }
      // I didn't find any way to provide custom tag to parser,
      // because javax.swing.text.html.parser.TagElement.TagElement(javax.swing.text.html.parser.Element, boolean)
      // calling javax.swing.text.html.HTML.getTag, where only standard tag accepted
      is HTML.UnknownTag -> {
        // but [javax.swing.text.html.HTML$Tag::toString] returns tag name
        if (t.toString() == HTML_TAG_LATEX && a.getAttribute(HTML_TAG_LATEX_ATTRIBUTE_TEXT) != null) {
          val latex = a.getAttribute(HTML_TAG_LATEX_ATTRIBUTE_TEXT) as String
          markdownBuilder.append(latex)
        }
      }
    }
  }

  override fun handleStartTag(t: HTML.Tag, a: MutableAttributeSet, pos: Int) {
    when (t) {
      HTML.Tag.H1 -> markdownBuilder.append("# ")
      HTML.Tag.H2 -> markdownBuilder.append("## ")
      HTML.Tag.H3 -> markdownBuilder.append("### ")
      HTML.Tag.H4 -> markdownBuilder.append("#### ")
      HTML.Tag.H5 -> markdownBuilder.append("##### ")
      HTML.Tag.H6 -> markdownBuilder.append("###### ")

      HTML.Tag.B, HTML.Tag.STRONG -> markdownBuilder.append("**")
      HTML.Tag.I, HTML.Tag.EM -> markdownBuilder.append("_")
      HTML.Tag.S, HTML.Tag.STRIKE -> markdownBuilder.append("~~")

      HTML.Tag.P, HTML.Tag.DIV -> if (listStack.isEmpty()) addNewLineIfNeeded()

      HTML.Tag.UL -> listStack.push(0)
      HTML.Tag.OL -> listStack.push(1)

      HTML.Tag.LI -> {
        if (listStack.isNotEmpty()) {
          addNewLineIfNeeded()
          repeat(listStack.size - 1) { markdownBuilder.append("    ") }
          var order = listStack.pop()
          if (order == 0)
            markdownBuilder.append("- ")
          else {
            markdownBuilder.append("$order. ")
            ++order
          }
          trimNextTextIndent = true
          listStack.push(order)
        }
      }

      HTML.Tag.PRE -> {
        ++inSaveSpaces
      }
      HTML.Tag.CODE -> {
        if (inSaveSpaces > 0) {
          addNewLineIfNeeded()
          markdownBuilder.append("```")
          addNewLine()
        }
        else {
          markdownBuilder.append("`")
        }
        ++inSaveSpaces
      }

      HTML.Tag.A -> {
        href = a.getAttribute(HTML.Attribute.HREF) as? String ?: ""
        markdownBuilder.append("[")
      }

      HTML.Tag.BLOCKQUOTE -> {
        ++inBlockquote
        generateLineEnd()
        addNewLine()
      }

      HTML.Tag.TABLE -> {
        rowCount = 0
        cellCountInRow = 0
        addNewLineIfNeeded()
        addNewLine()
      }
      HTML.Tag.TR -> {
        ++rowCount
        cellCountInRow = 0
        addNewLineIfNeeded()
      }
      HTML.Tag.TD, HTML.Tag.TH -> {
        ++cellCountInRow
        markdownBuilder.append("| ")
        trimNextTextIndent = true
      }
    }

    var terminator = ""

    fun checkStrike() {
      val clazz = a.getAttribute(HTML.Attribute.CLASS) as? String
      if (clazz != null && clazz.indexOf(CLASS_STRIKE) >= 0) {
        markdownBuilder.append("~~")
        terminator += "~~"
      }
    }
    val color = a.getAttribute(HTML.Attribute.COLOR) as? String
    if (color != null) {
      markdownBuilder.append("""<span style="color:$color">""")
      checkStrike()
      terminator += "</span>"
    }
    else {
      checkStrike()
    }
    endTerminators.push(Pair(t, terminator))
  }

  override fun handleEndTag(t: HTML.Tag, pos: Int) {

    if (endTerminators.isNotEmpty()) {
      val (tag, terminator) = endTerminators.pop()
      if (terminator.isNotEmpty()) {
        markdownBuilder.append(terminator)
      }
    }

    when (t) {
      HTML.Tag.H1,
      HTML.Tag.H2,
      HTML.Tag.H3,
      HTML.Tag.H4,
      HTML.Tag.H5,
      HTML.Tag.H6 -> addNewLineIfNeeded()

      HTML.Tag.B, HTML.Tag.STRONG -> markdownBuilder.append("**")
      HTML.Tag.I, HTML.Tag.EM -> markdownBuilder.append("_")
      HTML.Tag.S, HTML.Tag.STRIKE -> markdownBuilder.append("~~")

      HTML.Tag.P, HTML.Tag.DIV -> addNewLineIfNeeded()

      HTML.Tag.A -> {
        markdownBuilder.append("]($href)")
        href = ""
      }

      HTML.Tag.BLOCKQUOTE -> {
        --inBlockquote
        generateLineEnd()
        addNewLine()
        addNewLine()
      }

      HTML.Tag.UL, HTML.Tag.OL -> {
        listStack.pop()
        addLineSeparator()
      }
      HTML.Tag.LI -> addNewLineIfNeeded()

      HTML.Tag.PRE -> {
        --inSaveSpaces
      }
      HTML.Tag.CODE -> {
        --inSaveSpaces
        if (inSaveSpaces > 0) {
          addNewLineIfNeeded()
          markdownBuilder.append("```")
          addNewLine()
        }
        else {
          markdownBuilder.append("`")
        }
      }

      HTML.Tag.TABLE -> addNewLineIfNeeded()
      HTML.Tag.TR -> {
        markdownBuilder.append("|")
        addNewLine()
        if (rowCount == 1 && cellCountInRow > 0) {
          repeat(cellCountInRow) { markdownBuilder.append("| --- ") }
          markdownBuilder.append("|")
          addNewLine()
        }
      }
      HTML.Tag.TD, HTML.Tag.TH -> {
        markdownBuilder.append(" ")
        trimNextTextIndent = true
      }
    }
  }
}