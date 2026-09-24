// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.codeInspection.SuppressIntentionActionFromFix
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.codeInspection.SuppressableProblemGroup
import com.intellij.psi.PsiElement
import com.jetbrains.python.inspections.quickfix.PySuppressWithTypeIgnoreFix

/**
 * Provides the Alt-Enter "Suppress for this statement / for function 'f' / for class 'C'" actions for a single
 * [PyTypeCheckerInspection] problem tagged with [code]. The `# noinspection` actions reuse
 * [PyInspectionsSuppressor.createSuppressActions] for identical, declaration-naming wording, but insert a
 * `# noinspection <code.id>` comment (e.g. `bad-return`), which [PySuppressionUtil] then recognizes. A
 * "Suppress with `# type: ignore`" action ([PySuppressWithTypeIgnoreFix]) using the same granular code is
 * offered alongside them.
 *
 * [getProblemName] returns `null` on purpose: a non-null name would re-key the highlight's
 * `HighlightDisplayKey` to the code (for which no inspection tool is registered), and the highlight would be
 * dropped. The problem must stay keyed to the `PyTypeChecker` inspection for severity/enablement.
 */
internal class PyTypeCheckerSuppressableProblemGroup(private val code: PyTypeCheckerSuppressionCode) : SuppressableProblemGroup {
  /** The granular code id (e.g. `unsupported-operator`); read off a descriptor by [PyIgnoreCodeComputer]. */
  val codeId: String get() = code.id

  override fun getProblemName(): String? = null

  override fun getSuppressActions(element: PsiElement?): Array<SuppressIntentionAction> {
    val fixes: Array<SuppressQuickFix> = PyInspectionsSuppressor.createSuppressActions(code.id, element, true) +
                                         PySuppressWithTypeIgnoreFix.forCode(code.id)
    return SuppressIntentionActionFromFix.convertBatchToSuppressIntentionActions(fixes)
  }
}
