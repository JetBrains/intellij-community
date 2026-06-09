// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.editor

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.IndentInfo
import com.intellij.lang.Language
import com.intellij.mermaid.lang.MermaidFileType
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.mermaid.lang.formatter.MermaidSemanticEditorPosition
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.EXPAND_INDENT_AFTER
import com.intellij.mermaid.lang.lexer.MermaidTokens
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

    private fun getIndentString(
      editor: Editor,
      offset: Int,
      shouldExpand: Boolean,
      continuation: Boolean = false
    ): String {
      val indentOptions = getIndentOptions(editor)
      val docChars = editor.document.charsSequence
      var baseIndent = ""
      if (offset > 0) {
        val indentStart = CharArrayUtil.shiftBackwardUntil(docChars, offset, "\n") + 1
        if (indentStart >= 0) {
          val indentEnd = CharArrayUtil.shiftForward(docChars, indentStart, " \t")
          val diff = indentEnd - indentStart
          if (diff > 0) {
            baseIndent = docChars.subSequence(indentStart, indentEnd).toString()
          }
        }
      }
      if (shouldExpand) {
        val indent = if (!continuation) indentOptions.INDENT_SIZE else indentOptions.CONTINUATION_INDENT_SIZE
        baseIndent += IndentInfo(0, indent, 0).generateNewWhiteSpace(indentOptions)
      }
      return baseIndent
    }

    private fun moveAtStartOfPreviousLine(position: MermaidSemanticEditorPosition) {
      position.moveBeforeOptionalMix(MermaidTokens.WHITE_SPACE)
      if (position.isAt(MermaidTokens.EOL)) {
        position.moveBefore()
        position.moveBeforeOptionalMix(
          MermaidTokens.Sequence.MESSAGE,
          MermaidTokens.ID,
          MermaidTokens.DIR,
          MermaidTokens.WHITE_SPACE,
          MermaidTokens.SEMICOLON,
          MermaidTokens.RIGHT_OF,
          MermaidTokens.LEFT_OF
        )
      }
    }
  }

  override fun getLineIndent(project: Project, editor: Editor, language: Language?, offset: Int): String? {
    if (offset > 0) {
      val position: MermaidSemanticEditorPosition = getPosition(editor, offset - 1)
      if (position.isAt(MermaidTokens.EOL) || position.isAt(MermaidTokens.WHITE_SPACE)) {
        moveAtStartOfPreviousLine(position)
        if (position.isAtAnyOf(*EXPAND_INDENT_AFTER.types)) {
          return getIndentString(editor, position.getStartOffset(), true)
        } else if (position.isAt(MermaidTokens.Pie.SHOW_DATA)) {
          position.moveBeforeOptionalMix(MermaidTokens.Pie.SHOW_DATA, MermaidTokens.WHITE_SPACE)
          return getIndentString(editor, position.getStartOffset(), position.isAt(MermaidTokens.Pie.PIE))
        } else if (position.isAt(MermaidTokens.TASK_NAME)) {
          return getIndentString(editor, position.getStartOffset(), true, continuation = true)
        } else if (position.isAt(MermaidTokens.TITLE_VALUE)) {
          position.moveBeforeOptionalMix(MermaidTokens.TITLE_VALUE, MermaidTokens.TITLE, MermaidTokens.WHITE_SPACE)
          return getIndentString(editor, position.getStartOffset(), !position.isAt(MermaidTokens.EOL))
        }

        return getIndentString(editor, position.getStartOffset(), false)
      }
    }
    return null
  }

  override fun isSuitableFor(language: Language?): Boolean = language is MermaidLanguage
}
