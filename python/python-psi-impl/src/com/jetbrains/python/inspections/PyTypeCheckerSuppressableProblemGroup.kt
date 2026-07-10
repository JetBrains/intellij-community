// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.codeInspection.SuppressIntentionActionFromFix
import com.intellij.codeInspection.SuppressableProblemGroup
import com.intellij.psi.PsiElement

/**
 * Provides the Alt-Enter "Suppress for this statement / for function 'f' / for class 'C'" actions for a single
 * [PyTypeCheckerInspection] problem tagged with [code]. The actions reuse
 * [PyInspectionsSuppressor.createSuppressActions] for identical, declaration-naming wording, but insert a
 * `# noinspection <code.id>` comment (e.g. `bad-return`), which [PySuppressionUtil] then recognizes.
 *
 * [getProblemName] returns `null` on purpose: a non-null name would re-key the highlight's
 * `HighlightDisplayKey` to the code (for which no inspection tool is registered), and the highlight would be
 * dropped. The problem must stay keyed to the `PyTypeChecker` inspection for severity/enablement.
 */
internal class PyTypeCheckerSuppressableProblemGroup(private val code: PyTypeCheckerSuppressionCode) : SuppressableProblemGroup {
  override fun getProblemName(): String? = null

  override fun getSuppressActions(element: PsiElement?): Array<SuppressIntentionAction> {
    val fixes = PyInspectionsSuppressor.createSuppressActions(code.id, element, true)
    return SuppressIntentionActionFromFix.convertBatchToSuppressIntentionActions(fixes)
  }
}
