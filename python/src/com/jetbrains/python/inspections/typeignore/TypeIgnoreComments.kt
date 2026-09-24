// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childrenSequence
import com.jetbrains.python.inspections.PyIgnoreCommentUtil

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
  val parsed = PyIgnoreCommentUtil.parse(comment) ?: return null
  return PyIgnoreCommentUtil.codeRefs(parsed).mapNotNullTo(HashSet(), ::specificTarget)
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
 * Returns the suppress id that a single code targets, or `null` when [ref] names no PyCharm inspection. A
 * PyCharm code, with the `pycharm:` namespace or from `# pycharm: ignore`, always has a target. A bare code of
 * `# type: ignore` must match a registered suppress id.
 */
private fun specificTarget(ref: PyIgnoreCommentUtil.CodeRef): String? {
  if (ref.pycharmNamespaced) return ref.name
  return ref.name.takeIf { PyTypeIgnoreSuppressIds.getInstance().isKnownSuppressId(it) }
}
