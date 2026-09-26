// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyUtil

object PyNestedClassUtils {
  private const val MAX_NESTING_DEPTH = 10

  @JvmStatic
  fun findTopLevelClass(nested: PyClass): PyClass? {
    if (PyUtil.isTopLevel(nested)) {
      return nested
    }

    var current = nested
    var parent = PsiTreeUtil.getParentOfType(current, PyClass::class.java)
    while (parent != null) {
      current = parent
      parent = PsiTreeUtil.getParentOfType(current, PyClass::class.java)
    }

    return if (PyUtil.isTopLevel(current)) current else null
  }

  /**
   * Builds a qualified name from a nested class to the outermost class.
   *
   * Example: For `Outer.Middle.Inner`, returns "Outer.Middle.Inner"
   *
   * @param nested Inner class (e.g., Inner)
   * @param outermost Top-level class (e.g., Outer)
   * @return Qualified name like "Outer.Middle.Inner", or null if the hierarchy is invalid
   */
  @JvmStatic
  fun buildQualifiedName(nested: PyClass, outermost: PyClass): String? {
    if (nested.containingFile != outermost.containingFile) {
      return null
    }

    val names = ArrayDeque<String>()
    var current: PyClass? = nested
    var depth = 0

    while (current != null && depth < MAX_NESTING_DEPTH) {
      val name = current.name
      if (name.isNullOrEmpty()) {
        return null
      }
      names.addFirst(name)
      if (current == outermost) {
        return names.joinToString(".")
      }
      current = PsiTreeUtil.getParentOfType(current, PyClass::class.java)
      depth++
    }

    // nested is not a descendant of outermost
    return null
  }

  @JvmStatic
  fun findNestedByName(parent: PyClass, name: String): PyClass? {
    return findNestedByNameInternal(parent, name, MAX_NESTING_DEPTH)
  }

  private fun findNestedByNameInternal(parent: PyClass, name: String, maxDepth: Int): PyClass? {
    if (maxDepth <= 0) {
      return null
    }

    for (nested in parent.nestedClasses) {
      if (name == nested.name) {
        return nested
      }
      val deeper = findNestedByNameInternal(nested, name, maxDepth - 1)
      if (deeper != null) {
        return deeper
      }
    }
    return null
  }
}