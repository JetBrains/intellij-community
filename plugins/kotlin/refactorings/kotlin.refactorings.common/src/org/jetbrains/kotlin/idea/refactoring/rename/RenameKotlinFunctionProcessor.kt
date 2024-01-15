// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pass
import com.intellij.psi.*
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.*
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.SmartList
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.conflicts.checkRedeclarationConflicts
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport
import org.jetbrains.kotlin.idea.search.declarationsSearch.findDeepestSuperMethodsKotlinAware
import org.jetbrains.kotlin.psi.*

class RenameKotlinFunctionProcessor : RenameKotlinPsiProcessor() {

    private val javaMethodProcessorInstance = RenameJavaMethodProcessor()

    override fun canProcessElement(element: PsiElement): Boolean {
        return element is KtNamedFunction || (element is KtLightMethod && element.kotlinOrigin is KtNamedFunction) || element is FunctionWithSupersWrapper
    }

    override fun isToSearchInComments(psiElement: PsiElement) = KotlinCommonRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION

    override fun setToSearchInComments(element: PsiElement, enabled: Boolean) {
        KotlinCommonRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION = enabled
    }

    override fun isToSearchForTextOccurrences(element: PsiElement) = KotlinCommonRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FUNCTION

    override fun setToSearchForTextOccurrences(element: PsiElement, enabled: Boolean) {
        KotlinCommonRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FUNCTION = enabled
    }

    private fun getJvmName(element: PsiElement): String? {
        return renameRefactoringSupport.getJvmName(element)
    }

    private fun processFoundReferences(
        element: PsiElement,
        allReferences: Collection<PsiReference>
    ): Collection<PsiReference> {
        return when {
            getJvmName(element) == null -> allReferences
            element is KtElement -> allReferences.filterIsInstance<KtReference>()
            element is KtLightElement<*, *> -> allReferences.filterNot { it is KtReference }
            else -> emptyList()
        }
    }

    override fun findCollisions(
        element: PsiElement,
        newName: String,
        allRenames: Map<out PsiElement, String>,
        result: MutableList<UsageInfo>
    ) {
        val declaration = element.unwrapped as? KtNamedFunction ?: return
        checkConflictsAndReplaceUsageInfos(element, allRenames, result)
        result += SmartList<UsageInfo>().also { collisions ->
          checkRedeclarationConflicts(declaration, newName, collisions)
          renameRefactoringSupport.checkUsagesRetargeting(declaration, newName, result, collisions)
        }
    }

    private class FunctionWithSupersWrapper(
        val originalDeclaration: KtNamedFunction,
        val supers: List<PsiElement>
    ) : KtLightElement<KtNamedFunction, KtNamedFunction>, PsiNamedElement by originalDeclaration {
        override val kotlinOrigin: KtNamedFunction get() = originalDeclaration
    }

    private fun substituteForExpectOrActual(element: PsiElement?) =
        (element?.namedUnwrappedElement as? KtNamedDeclaration)?.let { el ->
            ActionUtil.underModalProgress(el.project, KotlinBundle.message("progress.title.searching.for.expected.actual")) { renameRefactoringSupport.liftToExpected(el) }
        }

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement? {
        substituteForExpectOrActual(element)?.let { return it }

        val wrappedMethod = wrapPsiMethod(element) ?: return element

        val deepestSuperMethods = runProcessWithProgressSynchronously(
            KotlinBundle.message("rename.searching.for.super.declaration"),
            canBeCancelled = true,
            element.project
        ) {
            runReadAction {
                findDeepestSuperMethodsKotlinAware(wrappedMethod)
            }
        }

        val substitutedJavaElement = when {
            deepestSuperMethods.isEmpty() -> return element
            wrappedMethod.isConstructor || deepestSuperMethods.size == 1 || element !is KtNamedFunction -> {
                javaMethodProcessorInstance.substituteElementToRename(wrappedMethod, editor)
            }
            else -> {
                val chosenElements = KotlinFindUsagesSupport.getInstance(element.project).checkSuperMethods(element, null, KotlinBundle.message("text.rename.as.part.of.phrase"))
                if (chosenElements.size > 1) FunctionWithSupersWrapper(element, chosenElements) else wrappedMethod
            }
        }

        if (substitutedJavaElement is KtLightMethod && element is KtDeclaration) {
            return substitutedJavaElement.kotlinOrigin as? KtNamedFunction
        }

        val canRename = try {
            PsiElementRenameHandler.canRename(element.project, editor, substitutedJavaElement)
        } catch (_: CommonRefactoringUtil.RefactoringErrorHintException) {
            false
        }

        return if (canRename) substitutedJavaElement else element
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor, renameCallback: Pass<in PsiElement>) {
        if (!PsiElementRenameHandler.canRename(element.getProject(), editor, element)) return

        fun preprocessAndPass(substitutedJavaElement: PsiElement) {
            val elementToProcess = if (substitutedJavaElement is KtLightMethod && element is KtDeclaration) {
                substitutedJavaElement.kotlinOrigin as? KtNamedFunction
            } else {
                substitutedJavaElement
            }
            renameCallback.accept(elementToProcess)
        }

        substituteForExpectOrActual(element)?.let { return preprocessAndPass(it) }

        val wrappedMethod = wrapPsiMethod(element)
        val deepestSuperMethods = runProcessWithProgressSynchronously(
            KotlinBundle.message("rename.searching.for.super.declaration"),
            canBeCancelled = true,
            element.project
        ) {
            runReadAction {
                if (wrappedMethod != null) {
                    findDeepestSuperMethodsKotlinAware(wrappedMethod)
                } else {
                    KotlinSearchUsagesSupport.SearchUtils.findDeepestSuperMethodsNoWrapping(element)
                }
            }
        }

        when {
            deepestSuperMethods.isEmpty() -> preprocessAndPass(element)
            wrappedMethod != null && (wrappedMethod.isConstructor || deepestSuperMethods.size == 1 || element !is KtNamedFunction) -> {
                javaMethodProcessorInstance.substituteElementToRename(wrappedMethod, editor, Pass.create(::preprocessAndPass))
            }
            else -> {
                val declaration = element.unwrapped as? KtNamedFunction ?: return
                checkSuperMethodsWithPopup(declaration, deepestSuperMethods.toList(), editor) {
                    preprocessAndPass(if (it.size > 1) FunctionWithSupersWrapper(declaration, it) else wrappedMethod ?: element)
                }
            }
        }
    }

    override fun createRenameDialog(
        project: Project,
        element: PsiElement,
        nameSuggestionContext: PsiElement?,
        editor: Editor?
    ): RenameDialog {
        val elementForDialog = (element as? FunctionWithSupersWrapper)?.originalDeclaration ?: element
        return object : RenameDialog(project, elementForDialog, nameSuggestionContext, editor) {
            override fun createRenameProcessor(newName: String) =
                RenameProcessor(getProject(), element, newName, isSearchInComments, isSearchInNonJavaFiles)
        }
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
        super.prepareRenaming(element, newName, allRenames, scope)

        if (element is KtLightMethod && getJvmName(element) == null) {
            (element.kotlinOrigin as? KtNamedFunction)?.let { allRenames[it] = newName }
        }
        if (element is FunctionWithSupersWrapper) {
            allRenames.remove(element)
        }
        val originalName = (element.unwrapped as? KtNamedFunction)?.name ?: return
        for (declaration in ((element as? FunctionWithSupersWrapper)?.supers ?: listOf(element))) {
            val psiMethod = wrapPsiMethod(declaration) ?: continue
            allRenames[declaration] = newName
            val baseName = psiMethod.name
            val newBaseName = if (renameRefactoringSupport.demangleInternalName(baseName) == originalName) {
                renameRefactoringSupport.mangleInternalName(
                    newName,
                    renameRefactoringSupport.getModuleNameSuffixForMangledName(baseName)!!
                )
            } else newName

            if (psiMethod.containingClass != null) {
                val overriders = runProcessWithProgressSynchronously(
                    KotlinBundle.message("rename.searching.for.all.overrides"),
                    canBeCancelled = true,
                    psiMethod.project
                ) {
                    renameRefactoringSupport.findAllOverridingMethods(psiMethod, scope)
                }

                for (originalOverrider in overriders) {
                    // for possible Groovy wrappers
                    val overrider = (originalOverrider as? PsiMirrorElement)?.prototype as? PsiMethod ?: originalOverrider

                    if (overrider is SyntheticElement) continue

                    val overriderName = overrider.name
                    val newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, baseName, newBaseName)
                    if (newOverriderName != null) {
                        RenameUtil.assertNonCompileElement(overrider)
                        allRenames[overrider] = newOverriderName
                    }
                }
            }
        }
        renameRefactoringSupport.prepareForeignUsagesRenaming(element, newName, allRenames, scope)
    }

    override fun renameElement(element: PsiElement, newName: String, usages: Array<UsageInfo>, listener: RefactoringElementListener?) {
        val simpleUsages = ArrayList<UsageInfo>(usages.size)
        val ambiguousImportUsages = SmartList<UsageInfo>()
        val simpleImportUsages = SmartList<UsageInfo>()
        renameRefactoringSupport.processForeignUsages(element, newName, usages, fallbackHandler = { usage ->
            if (usage is LostDefaultValuesInOverridingFunctionUsageInfo) {
                usage.apply()
                return@processForeignUsages
            }

            when (usage.importState()) {
                ImportState.AMBIGUOUS -> ambiguousImportUsages += usage
                ImportState.SIMPLE -> simpleImportUsages += usage
                ImportState.NOT_IMPORT -> {
                    if (!renameMangledUsageIfPossible(usage, element, newName)) {
                        simpleUsages += usage
                    }
                }
            }
        })

        element.ambiguousImportUsages = ambiguousImportUsages

        val usagesToRename = if (simpleImportUsages.isEmpty()) simpleUsages else simpleImportUsages + simpleUsages
        renamePossiblyLightElement(element, newName, usagesToRename.toTypedArray(), listener)

        usages.forEach { (it as? KtResolvableCollisionUsageInfo)?.apply() }

        (element.unwrapped as? KtNamedDeclaration)?.let {
            renameRefactoringSupport.dropOverrideKeywordIfNecessary(it)
        }
    }

    private fun wrapPsiMethod(element: PsiElement?): PsiMethod? = when (element) {
        is PsiMethod -> element
        is KtNamedFunction, is KtSecondaryConstructor -> runReadAction {
            LightClassUtil.getLightClassMethod(element as KtFunction)
        }
        else -> throw IllegalStateException("Can't be for element $element there because of canProcessElement()")
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