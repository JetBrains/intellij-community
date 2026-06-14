// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModCommandAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyFile

/**
 * Registers [PyTypeCheckerInspection] problems tagged with a granular [PyTypeCheckerSuppressionCode].
 *
 * Unlike the plain `PyInspectionVisitor.registerProblem` helpers, every problem reported here:
 *  - is skipped when the element is covered by a `# noinspection <code>` comment for its own code
 *    ([PySuppressionUtil]); the broad `# noinspection PyTypeChecker` is still handled afterwards by
 *    the platform via [PyInspectionsSuppressor];
 *  - carries a [PyTypeCheckerSuppressableProblemGroup] so Alt-Enter offers the per-code suppress actions.
 *
 * `PyTypeCheckerInspection` never enables `downgradeHighlightForTypeEngine`; callers already pass the
 * engine-effective highlight type (via `effectiveHighlightType`), so this reporter uses it verbatim.
 */
internal object PyTypeCheckerProblemReporter {
  /** Reports a message that has a distinct plain description and HTML tooltip. */
  fun report(
    holder: ProblemsHolder,
    code: PyTypeCheckerSuppressionCode,
    element: PsiElement?,
    message: PyInspectionMessages.ProblemMessage,
    type: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
    vararg fixes: LocalQuickFix,
  ): Boolean {
    if (element == null || !canRegister(element) || PySuppressionUtil.isSuppressed(element, code.id)) return false
    val descriptor = holder.manager.createProblemDescriptor(
      element, null as TextRange?, message.description, type, message.tooltip, holder.isOnTheFly, *fixes
    )
    descriptor.problemGroup = PyTypeCheckerSuppressableProblemGroup(code)
    holder.registerProblem(descriptor)
    return true
  }

  /** Reports a message with a distinct tooltip and a single [ModCommandAction] quick fix. */
  fun report(
    holder: ProblemsHolder,
    code: PyTypeCheckerSuppressionCode,
    element: PsiElement?,
    message: PyInspectionMessages.ProblemMessage,
    type: ProblemHighlightType,
    fix: ModCommandAction,
  ): Boolean {
    val localFix = LocalQuickFix.from(fix)
    return if (localFix != null) report(holder, code, element, message, type, localFix)
    else report(holder, code, element, message, type)
  }

  /** Reports a plain-text message (tooltip is derived from the description, as in `ProblemsHolder.registerProblem`). */
  fun report(
    holder: ProblemsHolder,
    code: PyTypeCheckerSuppressionCode,
    element: PsiElement?,
    message: @InspectionMessage String,
    type: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
    vararg fixes: LocalQuickFix,
  ): Boolean {
    if (element == null || !canRegister(element) || PySuppressionUtil.isSuppressed(element, code.id)) return false
    val descriptor = holder.manager.createProblemDescriptor(element, message, true, type, holder.isOnTheFly, *fixes)
    descriptor.problemGroup = PyTypeCheckerSuppressableProblemGroup(code)
    holder.registerProblem(descriptor)
    return true
  }

  /**
   * Like [report] for a [PyInspectionMessages.ProblemMessage], but the on-the-fly hover tooltip is produced lazily by
   * [tooltip] (e.g. a re-computed type-mismatch breakdown). The supplier is invoked only on-the-fly; in batch mode, or
   * when it yields `null`, the message's own [PyInspectionMessages.ProblemMessage.tooltip] is used. The plain
   * [PyInspectionMessages.ProblemMessage.description] always stays the one-line description.
   */
  fun reportWithTooltip(
    holder: ProblemsHolder,
    code: PyTypeCheckerSuppressionCode,
    element: PsiElement?,
    message: PyInspectionMessages.ProblemMessage,
    type: ProblemHighlightType,
    vararg fixes: LocalQuickFix,
    tooltip: () -> @NlsContexts.Tooltip String?,
  ): Boolean {
    if (element == null || !canRegister(element) || PySuppressionUtil.isSuppressed(element, code.id)) return false
    val onTheFlyTooltip = if (holder.isOnTheFly) tooltip() else null
    val descriptor = holder.manager.createProblemDescriptor(
      element, null as TextRange?, message.description, type, onTheFlyTooltip ?: message.tooltip, holder.isOnTheFly, *fixes
    )
    descriptor.problemGroup = PyTypeCheckerSuppressableProblemGroup(code)
    holder.registerProblem(descriptor)
    return true
  }

  /** Plain-text variant of the supplier-based [reportWithTooltip]; the lazy [tooltip] replaces the derived one on-the-fly. */
  fun reportWithTooltip(
    holder: ProblemsHolder,
    code: PyTypeCheckerSuppressionCode,
    element: PsiElement?,
    message: @InspectionMessage String,
    type: ProblemHighlightType,
    tooltip: () -> @NlsContexts.Tooltip String?,
  ): Boolean {
    if (element == null || !canRegister(element) || PySuppressionUtil.isSuppressed(element, code.id)) return false
    val onTheFlyTooltip = if (holder.isOnTheFly) tooltip() else null
    val descriptor = if (onTheFlyTooltip != null)
      holder.manager.createProblemDescriptor(element, null as TextRange?, message, type, onTheFlyTooltip, holder.isOnTheFly)
    else
      holder.manager.createProblemDescriptor(element, message, true, type, holder.isOnTheFly)
    descriptor.problemGroup = PyTypeCheckerSuppressableProblemGroup(code)
    holder.registerProblem(descriptor)
    return true
  }

  // Mirrors PyInspectionVisitor.canRegisterProblem.
  private fun canRegister(element: PsiElement): Boolean = element.textLength > 0 || element is PyFile
}
