package org.jetbrains.plugins.textmate.editor

import com.intellij.application.options.CodeStyle
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.formatting.IndentInfo
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider
import com.intellij.textmate.joni.JoniRegexFactory
import org.jetbrains.plugins.textmate.TextMateLanguage
import org.jetbrains.plugins.textmate.TextMateService
import org.jetbrains.plugins.textmate.editor.Utils.Companion.indentOfLine
import org.jetbrains.plugins.textmate.language.preferences.IndentAction
import org.jetbrains.plugins.textmate.language.preferences.IndentationRules
import org.jetbrains.plugins.textmate.language.preferences.OnEnterRule
import org.jetbrains.plugins.textmate.regex.CaffeineCachingRegexProvider
import org.jetbrains.plugins.textmate.regex.RegexProvider
import org.jetbrains.plugins.textmate.regex.TextMateString


/**
 * Provides line indentation for TextMate language.
 */
class TextMateLineIndentProvider : LineIndentProvider {
  private val regexProvider: RegexProvider = CaffeineCachingRegexProvider(JoniRegexFactory())

  override fun getLineIndent(project: Project, editor: Editor, language: Language?, offset: Int): String? {
    if (language !is TextMateLanguage) return null

    val actualScope = TextMateEditorUtils.getCurrentScopeSelector((editor as EditorEx)) ?: return null
    val registry = TextMateService.getInstance().preferenceRegistry
    val preferencesList = registry.getPreferences(actualScope)
    val indentationRules = preferencesList.fold(IndentationRules.empty()) { x, r -> x.updateWith(r.indentationRules) }
    val onEnterRules = preferencesList.mapNotNull { it.onEnterRules }.flatten()

    val document = editor.document
    val lineNumber = document.getLineNumber(offset)
    if (lineNumber <= 0) return null
    val prevLineText = document.immutableCharSequence
      .subSequence(document.getLineStartOffset(lineNumber - 1), document.getLineEndOffset(lineNumber - 1))
      .toString()

    /*
    * This works good with .editorConfig,
    * without .editorconfig indent style is not auto-detected
    * Proper solution would require:
    * - improving parser using `indentationRules` from the TextMate bundle,
    * - adding TextMateFormattingModelBuilder, which will fuel `DetectableIndentOptionsProvider`
    */
    val virtualFile = editor.virtualFile ?: return null
    val options: IndentOptions = CodeStyle.getSettings(project, virtualFile)
      .getIndentOptionsByFile(project, virtualFile, null)
    val indentChange = getIndentChange(prevLineText, indentationRules, onEnterRules)

    if (indentChange != null) {
      val baseLineIndent = prevLineText.indentOfLine(options)
      val indent = maxOf(baseLineIndent + indentChange * options.TAB_SIZE, 0) // IDEA-356651 avoid negative
      return IndentInfo(0, indent, 0).generateNewWhiteSpace(options)
    }

    return null
  }

  override fun isSuitableFor(language: Language?): Boolean {
    return language != null && language is TextMateLanguage
  }

  private fun getIndentChange(
    prevLineText: String,
    indentationRules: IndentationRules,
    onEnterRules: List<OnEnterRule>,
  ): Int? {
    try {
      return regexProvider.withString(prevLineText) { prevLine ->
        getIndentChange(prevLine, indentationRules, onEnterRules)
      }
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      thisLogger().error("Using regex failed", e)
      return null
    }
  }

  private fun getIndentChange(
    prevLine: TextMateString,
    indentationRules: IndentationRules,
    onEnterRules: List<OnEnterRule>,
  ): Int? {
    for (onEnterRule in onEnterRules) {
      if (matches(onEnterRule.beforeText.text, prevLine)) {
        when (onEnterRule.action.indent) {
          IndentAction.INDENT -> return 1
          IndentAction.NONE -> return null
          else -> {}
        }
      }
    }

    when {
      matches(indentationRules.increaseIndentPattern, prevLine) -> return 1
      matches(indentationRules.decreaseIndentPattern, prevLine) -> return -1
      else -> return null
    }
  }

  private fun matches(pattern: String?, line: TextMateString): Boolean {
    if (pattern == null) return false
    return regexProvider.withRegex(pattern) {
      it.match(line, { ProgressManager.checkCanceled() }).matched
    }
  }
}