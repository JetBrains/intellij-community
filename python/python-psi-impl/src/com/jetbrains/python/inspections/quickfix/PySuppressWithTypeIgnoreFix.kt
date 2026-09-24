// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyIgnoreCommentUtil
import com.jetbrains.python.inspections.PySuppressionUtil
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.impl.PyPsiUtils
import org.jetbrains.annotations.Nls

/**
 * "Suppress with `# type: ignore`" quick fix (PY-90780): appends a trailing same-line `# type: ignore[<code>]`
 * comment to the offending line. Two advanced settings, read at apply time, control the inserted text:
 * [INCLUDE_CODE_SETTING] (a code or a bare comment) and [PYCHARM_NAMESPACE_SETTING] (the `pycharm:` prefix).
 * If a `# type: ignore` / `# pycharm: ignore` comment already exists on the line, the code is merged into its
 * brackets; other trailing comments are kept.
 *
 * Use [forInspection] for a whole inspection (inserts its kebab-case alias, e.g. `unresolved-references`) or
 * [forCode] for a granular type-checker code (e.g. `unsupported-operator`).
 */
class PySuppressWithTypeIgnoreFix private constructor(private val code: String) : SuppressQuickFix {

  override fun getFamilyName(): @Nls String = PyPsiBundle.message("INSP.python.suppressor.suppress.with.type.ignore")

  override fun getName(): @IntentionName String = familyName

  override fun isAvailable(project: Project, context: PsiElement): Boolean = context.isValid

  override fun isSuppressAll(): Boolean = false

  // Suppression fixes conventionally show no diff preview (matches AbstractBatchSuppressByNoInspectionCommentFix).
  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
    IntentionPreviewInfo.EMPTY

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return
    if (!element.isValid) return

    val namespacedCode = if (AdvancedSettings.getBoolean(PYCHARM_NAMESPACE_SETTING)) "pycharm:$code" else code
    val existing = PyPsiUtils.findSameLineComment(element)
    if (existing != null) {
      val merged = mergeInto(existing, namespacedCode) ?: return
      val level = LanguageLevel.forElement(element)
      existing.replace(PyElementGenerator.getInstance(project).createFromText(level, PsiComment::class.java, merged))
    }
    else {
      appendTrailingComment(project, element, buildComment(namespacedCode))
    }
  }

  /** A new comment. It is bare when [INCLUDE_CODE_SETTING] is off. */
  private fun buildComment(namespacedCode: String): String =
    if (AdvancedSettings.getBoolean(INCLUDE_CODE_SETTING)) "# type: ignore[$namespacedCode]" else "# type: ignore"

  /**
   * Text that should replace [existing], or `null` when nothing changes. The fix adds the code to an existing
   * ignore comment even when [INCLUDE_CODE_SETTING] is off. That comment names a PyCharm code, because the problem
   * is still reported, so a bare comment would suppress more. A `# pycharm: ignore` comment gets the code without
   * the namespace. Any other comment stays after a new ignore comment.
   */
  private fun mergeInto(existing: PsiComment, namespacedCode: String): String? {
    val parsed = PyIgnoreCommentUtil.parse(existing) ?: return "${buildComment(namespacedCode)}  ${existing.text}"
    val isPyCharmDirective = parsed.directive == PyIgnoreCommentUtil.Directive.PYCHARM
    val keyword = if (isPyCharmDirective) "pycharm" else "type"
    val codes = LinkedHashSet(parsed.rawCodes)
    if (!codes.add(if (isPyCharmDirective) code else namespacedCode)) return null  // already suppressed with this code
    return "# $keyword: ignore[${codes.joinToString(", ")}]"
  }

  private fun appendTrailingComment(project: Project, element: PsiElement, comment: String) {
    val file = element.containingFile ?: return
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(file) ?: return
    val line = document.getLineNumber(element.textRange.endOffset)
    document.insertString(document.getLineEndOffset(line), "  $comment")
    documentManager.commitDocument(document)
  }

  companion object {
    /** The advanced setting that adds the inspection code in brackets. When it is off, the fix writes a bare comment. */
    const val INCLUDE_CODE_SETTING: String = "python.type.ignore.suppress.include.code"

    /** The advanced setting that adds the `pycharm:` namespace to the code. */
    const val PYCHARM_NAMESPACE_SETTING: String = "python.type.ignore.suppress.pycharm.namespace"

    /** A fix for a whole inspection; inserts its kebab-case alias (or the raw id when it has none). */
    @JvmStatic
    fun forInspection(toolId: String): PySuppressWithTypeIgnoreFix =
      PySuppressWithTypeIgnoreFix(PySuppressionUtil.toSuppressionCode(toolId) ?: toolId)

    /** A fix for a specific code (e.g. a granular [com.jetbrains.python.inspections.PyTypeCheckerSuppressionCode]). */
    @JvmStatic
    fun forCode(code: String): PySuppressWithTypeIgnoreFix = PySuppressWithTypeIgnoreFix(code)
  }
}
