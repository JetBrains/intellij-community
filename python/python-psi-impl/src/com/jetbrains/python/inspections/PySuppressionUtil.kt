// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.SuppressionUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyStatement
import java.util.regex.Pattern

/**
 * Shared helpers for Python `# noinspection <id>` suppression comments.
 *
 * Used by [PyInspectionsSuppressor] (whole-inspection ids such as `PyMethodOverriding` and their kebab-case
 * aliases such as `method-overriding`) and by [PyTypeCheckerProblemReporter] (the granular
 * [PyTypeCheckerSuppressionCode] ids), so the scope-scanning stays in one place.
 */
internal object PySuppressionUtil {
  private val SUPPRESS_PATTERN: Pattern = Pattern.compile(SuppressionUtil.COMMON_SUPPRESS_REGEXP)

  /**
   * Returns `true` if [element] is covered by a `# noinspection <suppressId>` comment placed above its
   * enclosing statement, function, or class.
   */
  fun isSuppressed(element: PsiElement, suppressId: String): Boolean =
    isSuppressedForParent(element, PyStatement::class.java, suppressId) ||
    isSuppressedForParent(element, PyFunction::class.java, suppressId) ||
    isSuppressedForParent(element, PyClass::class.java, suppressId)

  /**
   * Maps a legacy inspection id to its industry-standard kebab-case suppression alias by dropping the `Py`
   * product prefix and converting CamelCase to `kebab-case` (e.g. `PyMethodOverriding` -> `method-overriding`,
   * `PyUnresolvedReferences` -> `unresolved-references`).
   *
   * Returns `null` for ids that are not `Py`-prefixed inspection ids (e.g. platform inspections that also run
   * on Python files), which keep their original id only.
   */
  fun toSuppressionCode(toolId: String): String? {
    if (toolId.length < 3 || !toolId.startsWith("Py") || !toolId[2].isUpperCase()) return null
    return toolId.substring(2)
      .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1-$2")
      .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
      .lowercase()
  }

  private fun isSuppressedForParent(element: PsiElement, parentClass: Class<out PyElement>, suppressId: String): Boolean {
    val parent = PsiTreeUtil.getParentOfType(element, parentClass, false) ?: return false
    return isSuppressedForElement(parent, suppressId)
  }

  private fun isSuppressedForElement(stmt: PyElement, suppressId: String): Boolean {
    var prevSibling: PsiElement? = stmt.prevSibling
    if (prevSibling == null) {
      prevSibling = stmt.parent?.prevSibling
    }
    while (prevSibling is PsiComment || prevSibling is PsiWhiteSpace) {
      if (prevSibling is PsiComment && isSuppressedInComment(prevSibling.text.substring(1).trim(), suppressId)) {
        return true
      }
      prevSibling = prevSibling.prevSibling
    }
    return false
  }

  private fun isSuppressedInComment(commentText: String, suppressId: String): Boolean {
    val m = SUPPRESS_PATTERN.matcher(commentText)
    return m.matches() && SuppressionUtil.isInspectionToolIdMentioned(m.group(1), suppressId)
  }
}
