// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyElementTypes
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.fstrings.FORMAT_SPEC_OPTION_KEY
import com.jetbrains.python.codeInsight.fstrings.PyFormatSpecCatalog
import com.jetbrains.python.codeInsight.fstrings.PyFormatSpecCategory
import com.jetbrains.python.codeInsight.fstrings.PyFormatSpecOption
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFStringFragment
import com.jetbrains.python.psi.PyFStringFragmentFormatPart
import com.jetbrains.python.psi.PyStringDunderUtil.KNOWN_FORMAT_MINI_LANGUAGE_TYPES
import com.jetbrains.python.psi.PyStringDunderUtil.isAllowedFormatOverride
import com.jetbrains.python.psi.types.PyTypeUtil.asUnionSequence
import com.jetbrains.python.psi.types.TypeEvalContext

// The value types come from the shared catalog, so completion and the annotator agree on what supports
// the format mini-language.
private val DATETIME_TYPE_NAMES = setOf(PyNames.FQN.DATE, PyNames.FQN.DATETIME, PyNames.FQN.TIME)

// Options offered when the value type is unknown, and once the spec already carries some text:
// a small, broadly-applicable subset.
private val COMMON_SPECS = setOf(".", "d", "f", "s", "<", ">")

/**
 * Provides code completion for f-string format specifications.
 *
 * Offers completions after the colon in f-string format parts, e.g., f"{x:<caret>}"
 * Shows valid format spec options based on the expression type.
 */
class PyFStringFormatSpecCompletionContributor : CompletionContributor(), DumbAware {

  init {
    extend(CompletionType.BASIC, psiElement().inside(PyFStringFragmentFormatPart::class.java),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(
               parameters: CompletionParameters,
               context: ProcessingContext,
               result: CompletionResultSet
             ) {
               val position = parameters.position
               val formatPart = position.parentOfType<PyFStringFragmentFormatPart>() ?: return
               val fragment = formatPart.parentOfType<PyFStringFragment>() ?: return
               val expression = fragment.expression ?: return

               // Bail out if the caret is inside a nested replacement field (e.g. the `width` in
               // f"{x:{width}.2f}"): that expression has its own completions and must not get format
               // specs mixed into them.
               val enclosingFragment = position.parentOfType<PyFStringFragment>()
               if (enclosingFragment != null && PsiTreeUtil.isAncestor(formatPart, enclosingFragment, false)) {
                 return
               }

               // The spec text between the colon and the caret, with a nested replacement field left out.
               val existingText = getTextBeforeCaret(formatPart) ?: ""
               val applies = appliesTo(expression.getExpressionType(fragment))

               if (existingText.isEmpty()) {
                 // Immediately after the colon: offer every option that applies to the value type.
                 addOptions(result, PyFormatSpecCatalog.options.filter(applies))
               }
               else {
                 // The spec already carries text, so offer the common options that also apply. The offered
                 // options extend that text instead of matching it, so the prefix matcher has to be empty.
                 addOptions(result.withPrefixMatcher(""),
                            PyFormatSpecCatalog.options.filter { it.spec in COMMON_SPECS && applies(it) })
               }
             }
           })
  }

  /**
   * Returns the literal spec text between the format start (the colon) and the caret, or `null` when the
   * format part has no format start. A nested replacement field and the closing brace are left out, so an
   * identifier such as the `width` in `f"{x:{width}.2f}"` is never read as spec characters.
   */
  private fun getTextBeforeCaret(formatPart: PyFStringFragmentFormatPart): String? {
    var afterFormatStart = false
    val text = StringBuilder()

    for (child in formatPart.node.getChildren(null)) {
      if (child.elementType == PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START) {
        afterFormatStart = true
        continue
      }
      if (!afterFormatStart ||
          child.elementType == PyElementTypes.FSTRING_FRAGMENT ||
          child.elementType == PyTokenTypes.FSTRING_FRAGMENT_END) {
        continue
      }

      // The dummy identifier that the platform inserts marks the caret, so the text stops there.
      val childText = child.text
      val dummyIndex = childText.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)
      if (dummyIndex >= 0) {
        text.append(childText, 0, dummyIndex)
        return text.toString()
      }
      text.append(childText)
    }

    return if (afterFormatStart) text.toString() else null
  }

  /** The options that apply to a value of [expressionType]. */
  private fun appliesTo(expressionType: ExpressionType): (PyFormatSpecOption) -> Boolean = when (expressionType) {
    ExpressionType.STRING_OR_NUMERIC -> { option -> option.category != PyFormatSpecCategory.DATETIME }
    ExpressionType.DATETIME -> { option -> option.category == PyFormatSpecCategory.DATETIME }
    ExpressionType.UNKNOWN -> { option -> option.spec in COMMON_SPECS }
  }

  private fun addOptions(result: CompletionResultSet, options: List<PyFormatSpecOption>) {
    options.forEach { result.addElement(createFormatSpecElement(it)) }
  }

  private fun createFormatSpecElement(option: PyFormatSpecOption): LookupElement {
    return LookupElementBuilder.create(option.spec)
      .withTypeText(option.shortDescription, true)
      .also { it.putUserData(FORMAT_SPEC_OPTION_KEY, option) }
  }

  private enum class ExpressionType {
    STRING_OR_NUMERIC,
    DATETIME,
    UNKNOWN
  }

  private fun PyExpression.getExpressionType(fragment: PyFStringFragment): ExpressionType {
    // Conversion modifiers (!s, !r, !a) always produce a string before formatting.
    if (fragment.typeConversion != null) {
      return ExpressionType.STRING_OR_NUMERIC
    }

    val context = TypeEvalContext.codeCompletion(project, containingFile)
    val type = context.getType(this) ?: return ExpressionType.UNKNOWN

    // The match walks the ancestors, so a subclass such as `bool` counts as its numeric base.
    val members = type.asUnionSequence().filterNotNull().toList()
    return when {
      members.any { it.isAllowedFormatOverride(KNOWN_FORMAT_MINI_LANGUAGE_TYPES, context) } -> ExpressionType.STRING_OR_NUMERIC
      members.any { it.isAllowedFormatOverride(DATETIME_TYPE_NAMES, context) } -> ExpressionType.DATETIME
      else -> ExpressionType.UNKNOWN
    }
  }
}
