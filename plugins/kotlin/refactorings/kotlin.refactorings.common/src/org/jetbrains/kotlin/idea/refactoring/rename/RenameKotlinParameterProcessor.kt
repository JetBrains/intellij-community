// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.elements.KtLightParameter
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.util.or
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.conflicts.checkRedeclarationConflicts
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.utils.SmartList

class RenameKotlinParameterProcessor : RenameKotlinPsiProcessor() {
  override fun canProcessElement(element: PsiElement): Boolean = when {
    element is KtParameter && (element.ownerFunction is KtFunction || element.ownerFunction is KtPropertyAccessor) -> true

    // rename started from java (for example by automatic renamer)
    element is KtLightParameter -> true

    else -> false
  }

  override fun isToSearchInComments(psiElement: PsiElement) = KotlinCommonRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PARAMETER

  override fun setToSearchInComments(element: PsiElement, enabled: Boolean) {
    KotlinCommonRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PARAMETER = enabled
  }

  override fun findCollisions(
    element: PsiElement,
    newName: String,
    allRenames: MutableMap<out PsiElement, String>,
    result: MutableList<UsageInfo>
  ) {
    val declaration = element.namedUnwrappedElement as? KtNamedDeclaration ?: return

    val collisions = SmartList<UsageInfo>()
    checkRedeclarationConflicts(declaration, newName, collisions)
    renameRefactoringSupport.checkUsagesRetargeting(declaration, newName, result, collisions)
    result += collisions
  }

  override fun findReferences(
    element: PsiElement,
    searchScope: SearchScope,
    searchInCommentsAndStrings: Boolean
  ): Collection<PsiReference> {
    val kotlinElement = element.unwrapped ?: return emptyList()
    val correctScope = if (kotlinElement is KtParameter) {
      searchScope or kotlinElement.useScopeForRename
    }
    else {
      searchScope
    }

    return super.findReferences(element, correctScope, searchInCommentsAndStrings)
  }

  override fun renameElement(element: PsiElement, newName: String, usages: Array<UsageInfo>, listener: RefactoringElementListener?) {
    super.renameElement(element, newName, usages, listener)

    usages.forEach { (it as? KtResolvableCollisionUsageInfo)?.apply() }
  }

  override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
    super.prepareRenaming(element, newName, allRenames, scope)
    ForeignUsagesRenameProcessor.prepareRenaming(element, newName, allRenames, scope)
  }
}
