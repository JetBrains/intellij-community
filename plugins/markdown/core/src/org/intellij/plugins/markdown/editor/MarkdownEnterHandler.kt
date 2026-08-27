// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.enter.EnterBetweenBracesFinalHandler
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.formatting.IndentInfo
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownBlockQuote
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.supportsMarkdown
import org.intellij.plugins.markdown.util.MarkdownPsiUtil

/**
 * Enter handler of Markdown plugin,
 *
 * It generates blockquotes on `enter`.
 * Also it stops indentation when there is >= 2 new lines after text
 */
internal class MarkdownEnterHandler : EnterHandlerDelegate {
  override fun shouldFormatInjectedFragment(file: PsiFile): Boolean {
    val injectionHost = InjectedLanguageManager.getInstance(file.project).getInjectionHost(file)
    return !isIndentedCodeFence(injectionHost)
  }

  /**
   * During preprocessing indentation can be stopped if there are more than
   * two new lines after last element of text in Markdown file.
   *
   * E.g. it means, that there will be no indent if you will hit enter two times
   * after list item on any indent level.
   */
  override fun preprocessEnter(file: PsiFile, editor: Editor, caretOffset: Ref<Int>, caretAdvance: Ref<Int>,
                               dataContext: DataContext, originalHandler: EditorActionHandler?): Result {
    val offset = editor.caretModel.offset
    val element = MarkdownPsiUtil.findNonWhiteSpacePrevSibling(file, offset) ?: return Result.Continue
    if (!file.isValid || !shouldHandle(editor, dataContext, element)) return Result.Continue

    if (shouldAbortIndentation(file, editor, caretOffset.get())) {
      EditorModificationUtil.insertStringAtCaret(editor, "\n")
      return Result.Stop
    }

    return Result.Continue
  }

  /**
   * During post-processing `>` can be added if it is necessary
   */
  override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result {
    val injectionHost = InjectedLanguageManager.getInstance(file.project).getInjectionHost(file)
    if (!file.isValid && injectionHost?.isValid != true) return Result.Continue

    val element = findPostProcessElement(file, editor, injectionHost) ?: return Result.Continue
    if (injectionHost == null && !shouldHandle(editor, dataContext, element)) return Result.Continue

    val fence = findFence(element, injectionHost)
    if (fence == null) {
      processBlockQuote(editor, element)
      return Result.Continue
    }

    val isIndentedFence = isIndentedCodeFence(injectionHost)
    val indent = MarkdownCodeFenceUtils.getIndent(fence) ?: ""
    val topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
    val caretOffset = topLevelEditor.caretModel.offset
    val document = topLevelEditor.document
    val caretLine = document.getLineNumber(caretOffset)
    val braceIndentPlan = if (!isIndentedFence) {
      null
    }
    else getBraceIndentPlan(file, editor, document, caretLine, indent)
    if (braceIndentPlan != null) {
      val nextLineStart = document.getLineStartOffset(caretLine + 1)
      document.replaceString(
        nextLineStart,
        nextLineStart + braceIndentPlan.existingClosingIndent.length,
        braceIndentPlan.desiredClosingIndent
      )
      val lineStart = document.getLineStartOffset(caretLine)
      document.replaceString(
        lineStart,
        lineStart + braceIndentPlan.existingCurrentIndent.length,
        braceIndentPlan.desiredCurrentIndent
      )
    }
    else if (indent.isNotEmpty()) {
      document.insertString(document.getLineStartOffset(caretLine), indent)
    }
    val newCaretOffset = if (braceIndentPlan != null) {
      document.getLineStartOffset(caretLine) + braceIndentPlan.desiredCurrentIndent.length
    }
    else {
      caretOffset + indent.length
    }
    topLevelEditor.caretModel.moveToOffset(newCaretOffset)
    return Result.Continue
  }

  private fun findPostProcessElement(file: PsiFile, editor: Editor, injectionHost: PsiElement?): PsiElement? {
    if (!file.isValid) return injectionHost
    return MarkdownPsiUtil.findNonWhiteSpacePrevSibling(file, editor.caretModel.offset) ?: injectionHost
  }

  private fun getBraceIndentPlan(
    file: PsiFile,
    editor: Editor,
    document: Document,
    caretLine: Int,
    fenceIndent: String,
  ): BraceIndentPlan? {
    if (caretLine == 0 || caretLine + 1 >= document.lineCount) return null
    val currentLine = lineText(document, caretLine)
    val previousLine = lineText(document, caretLine - 1)
    val nextLine = lineText(document, caretLine + 1)
    val previousLineContent = previousLine.removePrefix(fenceIndent)
    val nextLineContent = nextLine.removePrefix(fenceIndent)
    val currentLineContent = currentLine.removePrefix(fenceIndent)
    if (currentLineContent.any { !it.isWhitespace() }) return null
    val openingBrace = previousLineContent.lastOrNull { !it.isWhitespace() } ?: return null
    val closingBrace = nextLineContent.firstOrNull { !it.isWhitespace() } ?: return null
    if (!EnterBetweenBracesFinalHandler.isBracePair(file.language, openingBrace, closingBrace)) return null

    fun existingIndent(line: CharSequence): String = line.takeWhile { it == ' ' || it == '\t' }.toString()

    val codeIndent = existingIndent(previousLineContent)
    val existingCurrentIndent = if (currentLine.startsWith(fenceIndent)) {
      fenceIndent + existingIndent(currentLineContent)
    }
    else {
      existingIndent(currentLine)
    }
    val existingClosingIndent = if (nextLine.startsWith(fenceIndent)) {
      fenceIndent + existingIndent(nextLineContent)
    }
    else {
      existingIndent(nextLine)
    }
    val desiredCodeIndent = CodeStyle.getLineIndent(editor, file.language, editor.caretModel.offset, false)
                            ?.takeIf { it.isNotEmpty() }
                            ?: run {
                              val indentOptions = CodeStyle.getIndentOptions(file)
                              codeIndent + IndentInfo(0, indentOptions.INDENT_SIZE, 0).generateNewWhiteSpace(indentOptions)
                            }
    // Language formatting is suppressed for Markdown code fence injections, so align the closing brace with the line
    // containing its opening brace. For continuation-indented openings this intentionally preserves that alignment.
    return BraceIndentPlan(
      desiredCurrentIndent = fenceIndent + desiredCodeIndent,
      desiredClosingIndent = fenceIndent + codeIndent,
      existingCurrentIndent = existingCurrentIndent,
      existingClosingIndent = existingClosingIndent,
    )
  }

  private fun lineText(document: Document, line: Int): CharSequence = document.charsSequence.subSequence(
    document.getLineStartOffset(line),
    document.getLineEndOffset(line)
  )

  private fun findFence(element: PsiElement, injectionHost: PsiElement?): MarkdownCodeFence? {
    return injectionHost as? MarkdownCodeFence ?: MarkdownCodeFenceUtils.getCodeFence(element)
  }

  private fun isIndentedCodeFence(injectionHost: PsiElement?): Boolean {
    return injectionHost is MarkdownCodeFence && MarkdownCodeFenceUtils.hasIndent(injectionHost)
  }

  private fun processBlockQuote(editor: Editor, element: PsiElement) {
    val quote = PsiTreeUtil.getParentOfType(element, MarkdownBlockQuote::class.java) ?: return
    val markdown = CodeStyle.getCustomSettings(quote.containingFile, MarkdownCustomCodeStyleSettings::class.java)

    var toAdd = ">"
    if (markdown.FORCE_ONE_SPACE_AFTER_BLOCKQUOTE_SYMBOL) {
      toAdd += " "
    }
    EditorModificationUtil.insertStringAtCaret(editor, toAdd)
  }

  /**
   * Check if alignment process should not be performed for this offset at all.
   *
   * Alignment of enter would not be performed if there is >= 2 new lines after
   * last text element.
   */
  private fun shouldAbortIndentation(file: PsiFile, editor: Editor, offset: Int): Boolean {
    //do not stop indentation after two spaces in code fences
    if (
      !file.supportsMarkdown()
      || file.findElementAt(offset - 1)?.let { MarkdownCodeFenceUtils.inCodeFence(it.node) } == true
    ) {
      return false
    }

    val text = editor.document.charsSequence.toString()

    var cur = offset - 1
    while (cur > 0) {
      val char = text.getOrNull(cur)

      if (char == null) {
        cur--
        continue
      }

      if (char.isWhitespace().not()) {
        break
      }

      if (char == '\n') {
        return true
      }

      cur--
    }

    return false
  }

  private fun shouldHandle(editor: Editor, dataContext: DataContext, element: PsiElement): Boolean {
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false

    if (!editor.document.isWritable) return false
    val topLevelFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(element)
    if (!topLevelFile.supportsMarkdown(dataContext)) return false

    return !editor.isViewer
  }

  private data class BraceIndentPlan(
    val desiredCurrentIndent: String,
    val desiredClosingIndent: String,
    val existingCurrentIndent: String,
    val existingClosingIndent: String,
  )
}
