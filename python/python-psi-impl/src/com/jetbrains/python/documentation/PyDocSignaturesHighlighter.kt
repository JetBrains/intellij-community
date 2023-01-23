package com.jetbrains.python.documentation

import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.highlighting.PyHighlighter
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.impl.PyBuiltinCache
import org.jetbrains.annotations.Nls

internal fun styledSpan(text: @Nls String, textAttributeKey: TextAttributesKey): HtmlChunk {
  return HtmlChunk.raw(styledSpanWithRawText(HtmlChunk.text(text).toString(), textAttributeKey))
}

internal fun styledSpan(text: HtmlChunk, textAttributeKey: TextAttributesKey): HtmlChunk {
  return HtmlChunk.raw(styledSpanWithRawText(text.toString(), textAttributeKey))
}

@Suppress("HardCodedStringLiteral")
internal fun highlightExpressionText(expressionText: @NlsSafe String, expression: PyExpression?): HtmlChunk =
  when (expression) {
    null -> HtmlChunk.text(expressionText)
    // Special case for PyStringLiteralExpression there because HtmlSyntaxInfoUtil will consider expressionText as docstring.
    // (The only string literal in a file is considered its docstring).
    is PyStringLiteralExpression -> styledSpan(expressionText, PyHighlighter.PY_UNICODE_STRING)
    else -> HtmlChunk.raw(HtmlSyntaxInfoUtil.getHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
      expression.project, PythonLanguage.INSTANCE, expressionText, highlightingSaturation()
    ))
  }

@NlsSafe
private fun styledSpanWithRawText(text: String, textAttributeKey: TextAttributesKey): String {
  return HtmlSyntaxInfoUtil.getStyledSpan(textAttributeKey, text, highlightingSaturation())
}

private fun highlightingSaturation() = DocumentationSettings.getHighlightingSaturation(false)

internal fun paramNameTextAttribute(isSelf: Boolean): TextAttributesKey =
  if (isSelf) PyHighlighter.PY_SELF_PARAMETER else PyHighlighter.PY_PARAMETER

internal fun functionNameTextAttribute(function: PyFunction, funcName: String?): TextAttributesKey {
  val languageLevel = LanguageLevel.forElement(function)
  if ((funcName in PyNames.UNDERSCORED_ATTRIBUTES || PyNames.getBuiltinMethods(languageLevel).containsKey(funcName))
      && function.containingClass != null) {
    return PyHighlighter.PY_PREDEFINED_USAGE
  }
  return PyHighlighter.PY_FUNC_DEFINITION
}

internal fun styledReference(refText: HtmlChunk, refTarget: PsiElement): HtmlChunk {
  if (PyBuiltinCache.getInstance(refTarget).isBuiltin(refTarget)) {
    return styledSpan(refText, PyHighlighter.PY_BUILTIN_NAME)
  }
  return refText
}
