// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyTargetExpression
import org.jetbrains.annotations.ApiStatus

/**
 * Renames an import alias (the `as <name>` target of `import foo as bar` or `from m import x as bar`).
 *
 * It behaves like a regular variable rename but never renames the imported name reference itself. Without this, when the alias
 * temporarily equals the imported name (e.g. `import foo as foo`, as produced while adding an alias), renaming the alias would
 * also rewrite the imported module/name and yield `import f as f` instead of `import foo as f`.
 *
 * It extends the variable processor to keep the search settings that every other Python rename target has.
 */
@ApiStatus.Internal
class RenamePyImportAliasProcessor : RenamePyVariableProcessor() {
  override fun canProcessElement(element: PsiElement): Boolean = isImportAlias(element)

  override fun findReferences(element: PsiElement,
                              searchScope: SearchScope,
                              searchInCommentsAndStrings: Boolean): Collection<PsiReference> {
    return super.findReferences(element, searchScope, searchInCommentsAndStrings).filter { isAliasReference(element, it) }
  }

  companion object {
    /**
     * Tells whether [reference] is a usage of the import alias [alias].
     *
     * The imported name of `import foo as foo` is not a usage, although the search finds it. The search also finds `x.foo`
     * by its name when `x` is unknown. Such a qualified name is not a usage, because it does not resolve.
     */
    @JvmStatic
    fun isAliasReference(alias: PsiElement, reference: PsiReference): Boolean {
      val element = reference.element
      val importedName = (alias.parent as? PyImportElement)?.importReferenceExpression
      if (importedName != null && PsiTreeUtil.isAncestor(importedName, element, false)) return false
      return element !is PyQualifiedExpression || !element.isQualified || reference.resolve() != null
    }
  }

  private fun isImportAlias(element: PsiElement): Boolean {
    val target = element as? PyTargetExpression ?: return false
    val importElement = target.parent as? PyImportElement ?: return false
    return importElement.asNameElement === target
  }
}
