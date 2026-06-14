// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.codeInspection.SuppressIntentionActionFromFix
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.codeInspection.SuppressableProblemGroup
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.inspections.quickfix.PySuppressInspectionFix
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStatement
import com.jetbrains.python.psi.PyStatementList

/**
 * Provides the Alt-Enter "Suppress '<code>' for a …" actions for a single [PyTypeCheckerInspection] problem
 * tagged with [code]. Each action inserts a `# noinspection <code>` comment, which
 * [PyTypeCheckerSuppressionUtil] then recognizes.
 *
 * [getProblemName] returns `null` on purpose: a non-null name would re-key the highlight's
 * `HighlightDisplayKey` to the code (for which no inspection tool is registered), and the highlight would be
 * dropped. The problem must stay keyed to the `PyTypeChecker` inspection for severity/enablement.
 */
internal class PyTypeCheckerSuppressableProblemGroup(private val code: PyTypeCheckerSuppressionCode) : SuppressableProblemGroup {
  override fun getProblemName(): String? = null

  override fun getSuppressActions(element: PsiElement?): Array<SuppressIntentionAction> {
    val id = code.id
    val fixes = arrayOf<SuppressQuickFix>(
      object : PySuppressInspectionFix(id, PyPsiBundle.message("INSP.python.suppressor.suppress.code.for.statement", id), PyStatement::class.java) {
        override fun getContainer(context: PsiElement?): PsiElement? {
          if (PsiTreeUtil.getParentOfType(context, PyStatementList::class.java, false, ScopeOwner::class.java) != null ||
              PsiTreeUtil.getParentOfType(context, PyFunction::class.java, PyClass::class.java) == null) {
            return super.getContainer(context)
          }
          return null
        }
      },
      PySuppressInspectionFix(id, PyPsiBundle.message("INSP.python.suppressor.suppress.code.for.function", id), PyFunction::class.java),
      PySuppressInspectionFix(id, PyPsiBundle.message("INSP.python.suppressor.suppress.code.for.class", id), PyClass::class.java),
    )
    return SuppressIntentionActionFromFix.convertBatchToSuppressIntentionActions(fixes)
  }
}
