// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.elements.KtLightParameter
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinMethodReferencesSearchParameters
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchParameters
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.ImportPath

abstract class RenameKotlinPsiProcessor : RenamePsiElementProcessor() {
    
    protected val renameRefactoringSupport: KotlinRenameRefactoringSupport
        get() = KotlinRenameRefactoringSupport.getInstance()

    class MangledJavaRefUsageInfo(
        val manglingSuffix: String,
        element: PsiElement,
        ref: PsiReference,
        referenceElement: PsiElement
    ) : MoveRenameUsageInfo(
        referenceElement,
        ref,
        ref.rangeInElement.startOffset,
        ref.rangeInElement.endOffset,
        element,
        false,
    )

    override fun canProcessElement(element: PsiElement): Boolean = element is KtNamedDeclaration

    protected fun findReferences(
        element: PsiElement,
        searchParameters: KotlinReferencesSearchParameters
    ): Collection<PsiReference> {
        val references = ReferencesSearch.search(searchParameters).toMutableSet()
        if (element is KtNamedFunction || (element is KtProperty && !element.isLocal) || (element is KtParameter && element.hasValOrVar())) {
            element.toLightMethods().flatMapTo(references) { method ->
                MethodReferencesSearch.search(
                    KotlinMethodReferencesSearchParameters(
                        method,
                        kotlinOptions = KotlinReferencesSearchOptions(
                            acceptImportAlias = false
                        )
                    )
                )
            }
        }
        return references.filter {
            // have to filter so far as
            // - text-matched reference could be named as imported alias and found in ReferencesSearch
            // - MethodUsagesSearcher could create its own MethodReferencesSearchParameters regardless provided one
            it.element.getNonStrictParentOfType<KtImportDirective>() != null || (it as? KtSimpleNameReference)?.getImportAlias() == null
        }
    }

    override fun createUsageInfo(element: PsiElement, ref: PsiReference, referenceElement: PsiElement): UsageInfo {
        if (ref !is KtReference) {
            val targetElement = ref.resolve()
            if (targetElement is KtLightMethod && targetElement.isMangled) {
                renameRefactoringSupport.getModuleNameSuffixForMangledName(targetElement.name)?.let {
                    return MangledJavaRefUsageInfo(
                        it,
                        element,
                        ref,
                        referenceElement
                    )
                }
            }
        }
        return super.createUsageInfo(element, ref, referenceElement)
    }

    override fun getElementToSearchInStringsAndComments(element: PsiElement): PsiElement? {
        val unwrapped = element.unwrapped ?: return null
        if ((unwrapped is KtDeclaration) && KtPsiUtil.isLocal(unwrapped)) return null
        return element
    }

    override fun getQualifiedNameAfterRename(element: PsiElement, newName: String, nonJava: Boolean): String? {
        if (!nonJava) return newName

        val qualifiedName = when (element) {
            is KtNamedDeclaration -> element.fqName?.asString() ?: element.name
            is PsiClass -> element.qualifiedName ?: element.name
            else -> return null
        }
        return PsiUtilCore.getQualifiedNameAfterRename(qualifiedName, newName)
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
        val safeNewName = newName.quoteIfNeeded()

        if (!newName.isIdentifier()) {
            allRenames[element] = safeNewName
        }

        val declaration = element.namedUnwrappedElement as? KtNamedDeclaration
        if (declaration != null) {
            renameRefactoringSupport.liftToExpected(declaration)?.let { expectDeclaration ->
                allRenames[expectDeclaration] = safeNewName
                renameRefactoringSupport.actualsForExpected(expectDeclaration).forEach { allRenames[it] = safeNewName }
            }
        }
    }

    protected var PsiElement.ambiguousImportUsages: List<UsageInfo>? by UserDataProperty(Key.create("AMBIGUOUS_IMPORT_USAGES"))

    protected fun UsageInfo.importState(): ImportState {
        val ref = reference as? PsiPolyVariantReference ?: return ImportState.NOT_IMPORT
        val refElement = ref.element
        if (refElement.parents.none { it is KtImportDirective && !it.isAllUnder || it is PsiImportStatementBase && !it.isOnDemand }) {
            return ImportState.NOT_IMPORT
        }

        return if (ref.multiResolve(false).mapNotNullTo(HashSet()) { it.element?.unwrapped }.size > 1)
            ImportState.AMBIGUOUS
        else
            ImportState.SIMPLE
    }

    protected enum class ImportState {
        NOT_IMPORT, AMBIGUOUS, SIMPLE
    }

    protected fun renameMangledUsageIfPossible(usage: UsageInfo, element: PsiElement, newName: String): Boolean {
        val chosenName = (if (usage is MangledJavaRefUsageInfo) {
            renameRefactoringSupport.mangleInternalName(newName, usage.manglingSuffix)
        } else {
            val reference = usage.reference
            if (reference is KtReference) {
                if (element is KtLightMethod && element.isMangled) {
                    renameRefactoringSupport.demangleInternalName(newName)
                } else null
            } else null
        }) ?: return false
        usage.reference?.handleElementRename(chosenName)
        return true
    }

    override fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<UsageInfo>,
        listener: RefactoringElementListener?
    ) {
        val simpleUsages = ArrayList<UsageInfo>(usages.size)
        renameRefactoringSupport.processForeignUsages(element, newName, usages, fallbackHandler = { usage ->
            if (renameMangledUsageIfPossible(usage, element, newName)) return@processForeignUsages
            simpleUsages += usage
        })

        renamePossiblyLightElement(element, newName, simpleUsages.toTypedArray(), listener)
    }

    /**
     * This method additionally dispatches rename for selected light elements,
     * since they are not supposed to be renamed via [PsiNamedElement.setName].
     */
    protected fun renamePossiblyLightElement(
        element: PsiElement,
        newName: String,
        usagesToRename: Array<UsageInfo>,
        listener: RefactoringElementListener?
    ) {
        when (element) {
            is KtLightParameter -> {
                val kotlinElement = element.kotlinOrigin ?: return
                RenameUtil.doRenameGenericNamedElement(kotlinElement, newName, usagesToRename, listener)
            }

            is KtLightMethod -> {
                val kotlinElement = element.kotlinOrigin ?: return
                val defensiveCopy = kotlinElement.copy()

                RenameUtil.doRenameGenericNamedElement(defensiveCopy, newName, usagesToRename, null)
                RenameLightElementsHelper.renameLightMethod(element, newName)

                listener?.elementRenamed(kotlinElement)
            }

            is KtLightClassForFacade -> {
                val kotlinElement = element.kotlinOrigin ?: element.files.firstOrNull() ?: return
                val defensiveCopy = kotlinElement.copy()

                RenameUtil.doRenameGenericNamedElement(defensiveCopy, newName, usagesToRename, null)
                RenameLightElementsHelper.renameFacadeLightClass(element, newName)

                listener?.elementRenamed(element)
            }

            is KtLightClass -> {
                val kotlinElement = element.kotlinOrigin ?: return
                RenameUtil.doRenameGenericNamedElement(kotlinElement, newName, usagesToRename, listener)
            }

            else -> {
                RenameUtil.doRenameGenericNamedElement(element, newName, usagesToRename, listener)
            }
        }
    }

    override fun getPostRenameCallback(element: PsiElement, newName: String, elementListener: RefactoringElementListener): Runnable? {
        return Runnable {
            element.ambiguousImportUsages?.forEach {
                val ref = it.reference as? PsiPolyVariantReference ?: return@forEach
                if (ref.multiResolve(false).isEmpty()) {
                    if (!renameMangledUsageIfPossible(it, element, newName)) {
                        ref.handleElementRename(newName)
                    }
                } else {
                    ref.element.getStrictParentOfType<KtImportDirective>()?.let { importDirective ->
                        val fqName = importDirective.importedFqName!!
                        val newFqName = fqName.parent().child(Name.identifier(newName))
                        val importList = importDirective.parent as KtImportList
                        if (importList.imports.none { directive -> directive.importedFqName == newFqName }) {
                            val newImportDirective = KtPsiFactory(element.project).createImportDirective(ImportPath(newFqName, false))
                            importDirective.parent.addAfter(newImportDirective, importDirective)
                        }
                    }
                }
            }
            element.ambiguousImportUsages = null
        }
    }

    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean
    ): Collection<PsiReference> {
        val searchParameters = KotlinReferencesSearchParameters(
            element,
            searchScope,
            kotlinOptions = KotlinReferencesSearchOptions(
                searchForComponentConventions = false,
                acceptImportAlias = false
            )
        )
        return findReferences(element, searchParameters)
    }

}
