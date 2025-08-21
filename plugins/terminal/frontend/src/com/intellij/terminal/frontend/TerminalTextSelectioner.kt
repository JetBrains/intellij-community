package com.intellij.terminal.frontend

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler
import com.intellij.codeInsight.editorActions.SelectWordUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.terminal.block.reworked.lang.TerminalOutputLanguage

/**
 * Replaces the default text selection logic invoked on double-click in the terminal editor.
 * The default [WordSelectioner][com.intellij.codeInsight.editorActions.wordSelection.WordSelectioner] is too strict for the terminal output.
 * For example, it considers `/` and `-` as separators while one of main double-click use cases in the terminal
 * is selecting the file path or, for example, git branch name.
 */
internal class TerminalTextSelectioner : ExtendWordSelectionHandler {
  override fun canSelect(e: PsiElement): Boolean {
    return isTerminalPsiElement(e)
  }

  override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
    val wordPartCondition = SelectWordUtil.CharCondition { !isSeparator(it) }
    val range = SelectWordUtil.getWordSelectionRange(editorText, cursorOffset, wordPartCondition)
    return if (range != null) listOf(range) else null
  }

  /**
   * Taken from [com.jediterm.terminal.model.SelectionUtil]
   * Only `\n` was added because the original list didn't include it.
   * (it just doesn't need to check the line breaks because [com.jediterm.terminal.model.TerminalTextBuffer] doesn't contain them)
   */
  private fun isSeparator(c: Char): Boolean {
    return c == ' ' ||
           c == '\u00A0' || // No-break space
           c == '\n' ||
           c == '\t' ||
           c == '\'' ||
           c == '"' ||
           c == '$' ||
           c == '(' ||
           c == ')' ||
           c == '[' ||
           c == ']' ||
           c == '{' ||
           c == '}' ||
           c == '<' ||
           c == '>'
  }
}

/**
 * Disable [com.intellij.codeInsight.editorActions.wordSelection.WordSelectioner] that can suggest selections shorter than needed.
 */
internal class TerminalWordSelectionFilter : Condition<PsiElement> {
  override fun value(t: PsiElement): Boolean {
    return !isTerminalPsiElement(t)
  }
}

private fun isTerminalPsiElement(e: PsiElement): Boolean {
  // the single `e.language` condition is enough, but it might be more expensive
  // because psi element implementations may compute the language differently.
  return e is LeafPsiElement && e.language == TerminalOutputLanguage
}