package org.jetbrains.plugins.textmate.editor

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.IndentInfo
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider
import org.jetbrains.plugins.textmate.TextMateLanguage
import org.jetbrains.plugins.textmate.TextMateService
import org.jetbrains.plugins.textmate.editor.Utils.Companion.indentOfLine
import org.jetbrains.plugins.textmate.language.preferences.IndentAction
import org.jetbrains.plugins.textmate.language.preferences.IndentationRules
import org.jetbrains.plugins.textmate.language.preferences.OnEnterRule


/**
 * Provides line indentation for TextMate language.
 */
class TextMateLineIndentProvider : LineIndentProvider {
  override fun getLineIndent(project: Project, editor: Editor, language: Language?, offset: Int): String? {
    if (language !is TextMateLanguage) return null

    val actualScope = TextMateEditorUtils.getCurrentScopeSelector((editor as EditorEx)) ?: return null
    val registry = TextMateService.getInstance().preferenceRegistry
    val preferencesList = registry.getPreferences(actualScope)
    val indentationRules = preferencesList.fold(IndentationRules.empty()) { x, r -> x.updateWith(r.indentationRules) }
    val onEnterRules = preferencesList.mapNotNull { it.onEnterRules }.flatten()

    val document = editor.document
    val lineNumber = document.getLineNumber(offset)
    if (lineNumber <= 0L) return null
    val lineOffset = document.getLineStartOffset(lineNumber)
    if (lineOffset <= 0L) return null
    val prevLineText = document.text.lines()[lineNumber - 1]

    /*
    * This works good with .editorConfig,
    * without .editorconfig indent style is not auto-detected
    * Proper solution would require:
    * - improving parser using `indentationRules` from the TextMate bundle,
    * - adding TextMateFormattingModelBuilder, which will fuel `DetectableIndentOptionsProvider`
    */
    val options: IndentOptions = CodeStyle.getSettings(project, editor.virtualFile)
      .getIndentOptionsByFile(project, editor.virtualFile, null)
    val indentChange = getIndentChange(prevLineText, indentationRules, onEnterRules)

    if (indentChange != null) {
      val baseLineIndent = prevLineText.indentOfLine(options)
      return IndentInfo(0, baseLineIndent + indentChange * options.TAB_SIZE, 0).generateNewWhiteSpace(options)
    }

    return null
  }

  override fun isSuitableFor(language: Language?): Boolean {
    return language != null && language is TextMateLanguage
  }

  private fun getIndentChange(prevLineText: String,
                              indentationRules: IndentationRules,
                              onEnterRules: List<OnEnterRule>): Int? {


    for (onEnterRule in onEnterRules){
      val beforeTextPatter = onEnterRule.beforeText.text
      if (prevLineText.contains(Regex(beforeTextPatter))) {
        when (onEnterRule.action.indent) {
          IndentAction.INDENT -> return 1
          IndentAction.NONE -> return null
          else -> {}
        }
      }
    }

    val increasePattern = indentationRules.increaseIndentPattern
    if (increasePattern != null && prevLineText.matches(Regex(increasePattern))) {
      return 1
    }

    val decreasePattern = indentationRules.decreaseIndentPattern
    if (decreasePattern != null && prevLineText.matches(Regex(decreasePattern))) {
      return -1
    }
    return null
  }
}