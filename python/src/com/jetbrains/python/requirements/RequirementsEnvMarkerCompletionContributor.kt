// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.python.requirements.RequirementsFile
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType
import com.intellij.psi.util.prevLeafs
import com.intellij.python.requirements.parser.psi.RequirementsTypes
import com.jetbrains.python.packaging.requirement.PyRequirementEnvMarkerType

/**
 * Completes PEP 508 environment-marker names (`python_version`, `sys_platform`, …) from
 * [PyRequirementEnvMarkerType] after `;`, `and`, `or`, or `(` in a requirement's marker section.
 */
class RequirementsEnvMarkerCompletionContributor : CompletionContributor() {
  override fun beforeCompletion(context: CompletionInitializationContext) {
    if (context.file !is RequirementsFile) return
    // ENV_MARKER_NAME is lowercase-only; the default mixed-case dummy identifier would not lex as a
    // marker name, so force a lowercase placeholder when the caret sits in a marker-name slot.
    if (isMarkerNameContext(context.file.findElementAt(context.startOffset - 1))) {
      context.dummyIdentifier = "a"
    }
  }

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.originalFile !is RequirementsFile) return
    val position = parameters.position
    if (!isMarkerNameContext(position)) return

    // RequirementsReferenceContributor attaches a reference spanning the whole requirement, so the
    // platform's derived completion prefix would be `name==ver; …` and filter every marker out.
    // Restrict the prefix to the marker-name text typed up to the caret (mirrors the version contributor).
    val prefixed = result.withPrefixMatcher(markerPrefix(position, parameters.offset))
    for (type in PyRequirementEnvMarkerType.entries) {
      prefixed.addElement(LookupElementBuilder.create(type.name.lowercase()))
    }
  }

  /**
   * A marker *name* is only valid right after an opener (`;`, `and`, `or`, `(`). We must therefore
   * look at the token to the *left of the name*, never accept the name token itself: while typing
   * the operator `and`/`or`, its partial `a`/`o` also lexes as `ENV_MARKER_NAME`, and accepting that
   * would wrongly offer (and autocomplete) a marker name in an operator slot.
   */
  private fun isMarkerNameContext(element: PsiElement?): Boolean {
    if (element == null) return false
    val leftOfName = when {
      element.elementType in MARKER_NAME_OPENERS -> element // caret right after `;`/`and`/`or`/`(`
      // caret inside a (partial) marker name, or in the whitespace before one — look further left
      element.elementType == RequirementsTypes.ENV_MARKER_NAME || element is PsiWhiteSpace ->
        element.prevLeafs.firstOrNull { it !is PsiWhiteSpace && it.textLength > 0 }
      else -> null
    }
    return leftOfName?.elementType in MARKER_NAME_OPENERS
  }

  private fun markerPrefix(position: PsiElement, offsetInFile: Int): String {
    if (position.elementType != RequirementsTypes.ENV_MARKER_NAME) return ""
    val offsetInPosition = (offsetInFile - position.textRange.startOffset).coerceIn(0, position.textLength)
    return position.text.take(offsetInPosition)
  }
}

private val MARKER_NAME_OPENERS: TokenSet = TokenSet.create(
  RequirementsTypes.SEMICOLON,
  RequirementsTypes.AND,
  RequirementsTypes.OR,
  RequirementsTypes.LPARENTHESIS,
)
