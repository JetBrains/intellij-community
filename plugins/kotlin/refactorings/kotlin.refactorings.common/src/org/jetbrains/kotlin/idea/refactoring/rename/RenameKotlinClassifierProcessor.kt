// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.usageView.UsageInfo
import com.intellij.util.SmartList
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.conflicts.checkRedeclarationConflicts
import org.jetbrains.kotlin.idea.search.ExpectActualUtils
import org.jetbrains.kotlin.psi.*

class RenameKotlinClassifierProcessor : RenameKotlinPsiProcessor() {

  override fun canProcessElement(element: PsiElement): Boolean {
    return element is KtClassOrObject || element is KtLightClass || element is KtConstructor<*> || element is KtTypeAlias
  }

  override fun isToSearchInComments(psiElement: PsiElement) = KotlinCommonRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS

  override fun setToSearchInComments(element: PsiElement, enabled: Boolean) {
    KotlinCommonRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = enabled
  }

  override fun isToSearchForTextOccurrences(element: PsiElement) = KotlinCommonRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_CLASS

  override fun setToSearchForTextOccurrences(element: PsiElement, enabled: Boolean) {
    KotlinCommonRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_CLASS = enabled
  }

  override fun substituteElementToRename(element: PsiElement, editor: Editor?) = getClassOrObject(element)

  override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
    super.prepareRenaming(element, newName, allRenames)

    val classOrObject = getClassOrObject(element) as? KtClassOrObject ?: return
    val topLevelClassifiers = ActionUtil.underModalProgress(element.project, KotlinBundle.message("progress.title.searching.for.expected.actual")) {
        ExpectActualUtils.withExpectedActuals(classOrObject).filter { it.parent is KtFile }
    }

    topLevelClassifiers.forEach {
      val file = it.containingKtFile
      val virtualFile = file.virtualFile
      if (virtualFile != null) {
        val nameWithoutExtensions = virtualFile.nameWithoutExtension
        if (nameWithoutExtensions == it.name) {
          val newFileName = newName + "." + virtualFile.extension
          allRenames.put(file, newFileName)
          forElement(file).prepareRenaming(file, newFileName, allRenames)
        }
      }
    }
  }

  private fun processFoundReferences(
    element: PsiElement,
    references: Collection<PsiReference>
  ): Collection<PsiReference> {
    if (element is KtObjectDeclaration && element.isCompanion()) {
      return references.filter { !it.isCompanionObjectClassReference() }
    }
    return references
  }

  private fun PsiReference.isCompanionObjectClassReference(): Boolean {
    return renameRefactoringSupport.isCompanionObjectClassReference(this)
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

  private fun getClassOrObject(element: PsiElement?): PsiElement? = when (element) {
    is KtLightClass ->
      when {
        renameRefactoringSupport.isLightClassForRegularKotlinClass(element) -> element.kotlinOrigin
        element is KtLightClassForFacade -> element
        else -> throw AssertionError("Should not be suggested to rename element of type " + element::class.java + " " + element)
      }

    is KtConstructor<*> ->
      element.getContainingClassOrObject()

    is KtClassOrObject, is KtTypeAlias -> element

    else -> null
  }

  override fun renameElement(element: PsiElement, newName: String, usages: Array<UsageInfo>, listener: RefactoringElementListener?) {
    val simpleUsages = ArrayList<UsageInfo>(usages.size)
    val ambiguousImportUsages = SmartList<UsageInfo>()
    val simpleImportUsages = SmartList<UsageInfo>()
    for (usage in usages) when (usage.importState()) {
      ImportState.AMBIGUOUS -> ambiguousImportUsages += usage
      ImportState.SIMPLE -> simpleImportUsages += usage
      ImportState.NOT_IMPORT -> simpleUsages += usage
    }

    element.ambiguousImportUsages = ambiguousImportUsages

    val usagesToRename = if (simpleImportUsages.isEmpty()) simpleUsages else simpleImportUsages + simpleUsages
    super.renameElement(element, newName, usagesToRename.toTypedArray(), listener)

    usages.forEach { (it as? KtResolvableCollisionUsageInfo)?.apply() }
  }

  override fun findReferences(
    element: PsiElement,
    searchScope: SearchScope,
    searchInCommentsAndStrings: Boolean
  ): Collection<PsiReference> {
    val references = super.findReferences(element, searchScope, searchInCommentsAndStrings)
    return processFoundReferences(element, references)
  }

}
