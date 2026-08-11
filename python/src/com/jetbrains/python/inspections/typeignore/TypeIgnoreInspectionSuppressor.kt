// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.python.codeInsight.PyCodeInsightSettings
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyPsiUtils

private const val PYCHARM_NAMESPACE = "pycharm"

/**
 * Suppresses inspections on lines (or whole files) annotated with a `# type: ignore` comment.
 *
 * `# type: ignore[<code>]` suppresses only the inspection(s) whose suppress id matches `<code>`, where
 * `<code>` is the inspection's suppress id optionally prefixed with the `pycharm:` namespace. A
 * `# type: ignore` without a recognized code is a blanket ignore: it suppresses every inspection on the
 * line, unless strict code coverage is enabled
 * ([PyCodeInsightSettings.TYPE_IGNORE_STRICT_CODE_COVERAGE]), in which case it suppresses nothing.
 */
class TypeIgnoreInspectionSuppressor : InspectionSuppressor {

  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    if (element is PsiFile) return false
    val containingFile = element.containingFile
    if (containingFile !is PyFile) return false

    val strict = PyCodeInsightSettings.getInstance().TYPE_IGNORE_STRICT_CODE_COVERAGE

    val sameLineComment = PyPsiUtils.findSameLineComment(element)
    if (sameLineComment != null && suppresses(sameLineComment, toolId, strict)) return true
    return isSuppressedAtFileLevel(containingFile, toolId, strict)
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
    return SuppressQuickFix.EMPTY_ARRAY
  }
}

private fun isSuppressedAtFileLevel(file: PsiFile, toolId: String, strict: Boolean): Boolean {
  var node = file.firstChild
  while (node is PsiComment || node is PsiWhiteSpace) {
    if (node is PsiComment && suppresses(node, toolId, strict)) return true
    node = node.nextSibling
  }
  return false
}

private fun suppresses(comment: PsiComment, toolId: String, strict: Boolean): Boolean {
  val codes = parseTypeIgnoreCodes(comment) ?: return false
  val specificTargets = codes.mapNotNullTo(HashSet(), ::specificTarget)
  if (specificTargets.isNotEmpty()) return toolId in specificTargets
  // Bare `# type: ignore`, empty brackets, or only foreign codes (e.g. mypy's `attr-defined`).
  return !strict
}

/**
 * Returns the suppress id targeted by a single `# type: ignore[...]` [rawCode], or `null` if the code
 * does not name a known PyCharm inspection. A `pycharm:` namespace prefix is stripped and always yields a
 * target; a bare code counts only when it matches a registered suppress id.
 */
private fun specificTarget(rawCode: String): String? {
  val colon = rawCode.indexOf(':')
  if (colon < 0) {
    return rawCode.takeIf { PyTypeIgnoreSuppressIds.getInstance().isKnownSuppressId(it) }
  }
  if (!rawCode.substring(0, colon).equals(PYCHARM_NAMESPACE, ignoreCase = true)) return null
  return rawCode.substring(colon + 1).trim().takeIf { it.isNotEmpty() }
}

/**
 * @return `null` if [comment] is not a `# type: ignore` comment; an empty set for a bare or
 * empty-bracket ignore; otherwise the trimmed, non-empty codes listed in the brackets.
 */
private fun parseTypeIgnoreCodes(comment: PsiComment): Set<String>? {
  val text = comment.text ?: return null
  val matcher = PyTypingTypeProvider.TYPE_IGNORE_PATTERN.matcher(text)
  if (!matcher.matches()) return null
  val bracketGroup = matcher.group(1) ?: return emptySet()  // "[code, ...]" including brackets, or null
  val codes = HashSet<String>()
  for (part in bracketGroup.substring(1, bracketGroup.length - 1).split(',')) {
    val code = part.trim()
    if (code.isNotEmpty()) codes.add(code)
  }
  return codes
}
