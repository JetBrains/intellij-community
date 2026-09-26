// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childrenSequence
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider

private const val PYCHARM_NAMESPACE = "pycharm"

internal enum class IgnoreScope { LINE, FILE }

internal fun ignoreScope(comment: PsiComment): IgnoreScope? {
  if (followsCodeOnItsLine(comment)) return IgnoreScope.LINE
  val file = comment.containingFile ?: return null
  return IgnoreScope.FILE.takeIf { file.leadingFileLevelComments().any { it === comment } }
}

internal fun PsiFile.leadingFileLevelComments(): Sequence<PsiComment> =
  childrenSequence
    .takeWhile { it is PsiComment || it is PsiWhiteSpace }
    .filterIsInstance<PsiComment>()

internal fun typeIgnoreTargets(comment: PsiComment): Set<String>? {
  val codes = parseTypeIgnoreCodes(comment) ?: return null
  return codes.mapNotNullTo(HashSet(), ::specificTarget)
}

private fun followsCodeOnItsLine(comment: PsiComment): Boolean {
  var previous = PsiTreeUtil.prevLeaf(comment) ?: return false
  while (previous is PsiWhiteSpace) {
    if (previous.text.contains('\n')) return false
    previous = PsiTreeUtil.prevLeaf(previous) ?: return false
  }
  return previous !is PsiComment
}

/**
 * Returns the suppress id that a single `# type: ignore` code targets, or `null` when [rawCode] names no
 * PyCharm inspection. A `pycharm:` prefix is stripped; a bare code must match a registered suppress id.
 */
private fun specificTarget(rawCode: String): String? {
  val colon = rawCode.indexOf(':')
  if (colon < 0) {
    return rawCode.takeIf { PyTypeIgnoreSuppressIds.getInstance().isKnownSuppressId(it) }
  }
  if (!rawCode.substring(0, colon).trim().equals(PYCHARM_NAMESPACE, ignoreCase = true)) return null
  return rawCode.substring(colon + 1).trim().takeIf { it.isNotEmpty() }
}

private fun parseTypeIgnoreCodes(comment: PsiComment): Set<String>? {
  val text = comment.text ?: return null
  val matcher = PyTypingTypeProvider.TYPE_IGNORE_PATTERN.matcher(text)
  if (!matcher.matches()) return null
  val bracketGroup = matcher.group(1) ?: return emptySet()  // "[code, ...]" including brackets, or null
  return bracketGroup.substring(1, bracketGroup.length - 1).split(',')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .toSet()
}
