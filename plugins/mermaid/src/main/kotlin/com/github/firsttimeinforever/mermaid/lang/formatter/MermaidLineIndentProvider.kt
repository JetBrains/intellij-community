package com.github.firsttimeinforever.mermaid.lang.formatter

import com.github.firsttimeinforever.mermaid.lang.MermaidFileType
import com.github.firsttimeinforever.mermaid.lang.MermaidLanguage
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens
import com.intellij.application.options.CodeStyle
import com.intellij.formatting.IndentInfo
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider
import com.intellij.util.text.CharArrayUtil

class MermaidLineIndentProvider : LineIndentProvider {

  companion object {
    private fun getIndentOptions(editor: Editor): CommonCodeStyleSettings.IndentOptions {
      return CodeStyle.getSettings(editor).getIndentOptions(MermaidFileType)
    }

    private fun getPosition(editor: Editor, offset: Int): MermaidSemanticEditorPosition {
      return MermaidSemanticEditorPosition.createEditorPosition(editor, offset)
    }

    private fun getIndentString(editor: Editor, offset: Int, shouldExpand: Boolean): String {
      val indentOptions = getIndentOptions(editor)
      val docChars = editor.document.charsSequence
      var baseIndent = ""
      if (offset > 0) {
        val indentStart = CharArrayUtil.shiftBackwardUntil(docChars, offset, "\n") + 1
        if (indentStart >= 0) {
          val indentEnd = CharArrayUtil.shiftForward(docChars, indentStart, " \t")
          val diff = indentEnd - indentStart
          if (diff > 0) {
            if (shouldExpand) {
              baseIndent = docChars.subSequence(indentStart, indentEnd).toString()
            } else {
              if (diff >= indentOptions.INDENT_SIZE) {
                baseIndent = docChars.subSequence(indentStart, indentEnd - indentOptions.INDENT_SIZE).toString()
              }
            }
          }
        }
      }
      if (shouldExpand) {
        baseIndent += IndentInfo(0, indentOptions.INDENT_SIZE, 0).generateNewWhiteSpace(indentOptions)
      }
      return baseIndent
    }

    private fun moveAtEndOfPreviousLine(position: MermaidSemanticEditorPosition) {
      position.moveBeforeOptionalMix(MermaidTokens.WHITE_SPACE)
      if (position.isAt(MermaidTokens.EOL)) {
        position.moveBefore()
        position.moveBeforeOptionalMix(MermaidTokens.WHITE_SPACE)
      }
    }
  }

  override fun getLineIndent(project: Project, editor: Editor, language: Language?, offset: Int): String? {
    if (offset > 0) {
      val position: MermaidSemanticEditorPosition = getPosition(editor, offset - 1)
      if (position.isAt(MermaidTokens.EOL) || position.isAt(MermaidTokens.WHITE_SPACE)) {
        moveAtEndOfPreviousLine(position)
        if (position.isAtAnyOf(
            MermaidTokens.Pie.PIE,
            MermaidTokens.Pie.SHOW_DATA,
            MermaidTokens.Journey.JOURNEY,
            MermaidTokens.Journey.SECTION_TITLE,
            MermaidTokens.Flowchart.FLOWCHART,
            MermaidTokens.Flowchart.SUBGRAPH,
            MermaidTokens.Sequence.SEQUENCE,
            MermaidTokens.Sequence.LOOP,
            MermaidTokens.Sequence.ALT,
            MermaidTokens.Sequence.PAR,
            MermaidTokens.Sequence.RECT,
            MermaidTokens.Sequence.MESSAGE,
            MermaidTokens.ClassDiagram.CLASS_DIAGRAM,
            MermaidTokens.OPEN_CURLY,
            MermaidTokens.StateDiagram.STATE_DIAGRAM
          )
        ) {
          return getIndentString(editor, position.getStartOffset(), true)
        } else if (position.isAtAnyOf(MermaidTokens.END)) {
          return getIndentString(editor, position.getStartOffset(), false)
        } else if (position.isAt(MermaidTokens.TITLE_VALUE)) {
          position.moveBeforeOptionalMix(MermaidTokens.TITLE_VALUE, MermaidTokens.TITLE, MermaidTokens.WHITE_SPACE)
          return if (position.isAt(MermaidTokens.EOL)) {
            null
          } else {
            getIndentString(editor, position.getStartOffset(), true)
          }
        }
      }
    }
    return null
  }

  override fun isSuitableFor(language: Language?): Boolean = language is MermaidLanguage
}
