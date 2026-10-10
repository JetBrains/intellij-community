// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.model.SymbolPointer
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanChainBuilder
import com.intellij.refactoring.modplan.ModPlanEdit
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.ModPlanUiInterpreter
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsageInfoUtil
import com.intellij.refactoring.safeDelete.JavaSafeDeleteDelegate
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegateBase
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.map2Array
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.javaInterop.callableSymbol
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitInvokeCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.resolution.simple
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.allOverriddenSymbols
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.directlyOverriddenSymbols
import org.jetbrains.kotlin.analysis.api.symbols.isSubClassOf
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.analysis.api.utils.unwrapSmartCasts
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.psi.removeModifierKeyword
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.useScope
import org.jetbrains.kotlin.idea.k2.refactoring.KotlinFirRefactoringsSettings
import org.jetbrains.kotlin.idea.k2.refactoring.KotlinK2RefactoringsBundle
import org.jetbrains.kotlin.idea.k2.refactoring.canDeleteElement
import org.jetbrains.kotlin.idea.k2.refactoring.overriddenDeclarations
import org.jetbrains.kotlin.idea.refactoring.deleteBracesAroundEmptyList
import org.jetbrains.kotlin.idea.refactoring.deleteSeparatingComma
import org.jetbrains.kotlin.idea.refactoring.safeDelete.KotlinSafeDeleteSettings
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.ExpectActualUtils
import org.jetbrains.kotlin.idea.search.usagesSearch.processDelegationCallConstructorUsages
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinClassInheritorsSearch
import org.jetbrains.kotlin.idea.searching.inheritors.findAllOverridings
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.resolveSuccessfulExpressionCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtContextParameterList
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

class KotlinFirSafeDeleteProcessor : SafeDeleteProcessorDelegateBase() {
    override fun handlesElement(element: PsiElement?): Boolean = element.canDeleteElement()

    override fun findUsages(
        element: PsiElement,
        allElementsToDelete: Array<out PsiElement>,
        result: MutableList<in UsageInfo>
    ): NonCodeUsageSearchInfo = findKotlinUsages(element, allElementsToDelete, result)

    /**
     * Runs the chain of [kotlinElementsToSearch] with the dialogs of its questions. In a unit test nothing is asked: the questions keep the
     * default answers, and a parameter of an actual declaration is lifted to the expected one as the test settings say.
     */
    override fun getElementsToSearch(
        element: PsiElement, module: Module?, allElementsToDelete: Collection<PsiElement>
    ): Collection<PsiElement>? {
        val project = element.project
        val optionValues = if (isUnitTestMode() && element is KtParameter) {
            val liftsParameter = with(KotlinSafeDeleteSettings) { project.ALLOW_LIFTING_ACTUAL_PARAMETER_TO_EXPECTED }
            ModPlanOptionValues.EMPTY.with(ModPlanKotlinSafeDeleteOptions.DELETE_EXPECT_ACTUAL, liftsParameter)
        }
        else {
            ModPlanOptionValues.EMPTY
        }
        val request = ModPlanRequest(project, listOf(SymbolPointer.of(element)), optionValues)
        val subjectsToDelete = allElementsToDelete.map { SymbolPointer.of(it) }
        val finished = ModPlanUiInterpreter.getInstance().interpret(
            RefactoringBundle.message("safe.delete.title"), request
        ) { chainBuilder ->
            val currentElement = chainBuilder.request.subjects().first().element()
                                 ?: return@interpret chainBuilder.passValue(emptyList())
            val currentElementsToDelete = ModPlanSafeDeleteUsageInfoUtil.elementsOf(subjectsToDelete).toList()
            kotlinElementsToSearch(chainBuilder, currentElement, currentElementsToDelete)
        } ?: return null
        if (!finished.answers.get(ModPlanKotlinSafeDeleteOptions.DELETE_EXPECT_ACTUAL)) {
            // A headless plan drops the modifier when it is applied; the legacy flow drops it in prepareForDeletion.
            for (parameter in finished.value.filterIsInstance<KtParameter>()) {
                val ownerFunction = parameter.ownerFunction
                if (ownerFunction?.hasActualModifier() == true) {
                    with(KotlinSafeDeleteSettings) { ownerFunction.dropActualModifier = true }
                }
            }
        }
        return finished.value.ifEmpty { null }
    }

    override fun findConflicts(
        element: PsiElement,
        allElementsToDelete: Array<out PsiElement>,
        usages: Array<out UsageInfo>,
        conflicts: MultiMap<PsiElement, @NlsContexts.DialogMessage String>
    ) {
        collectKotlinConflicts(element, conflicts, headless = false)
    }

    override fun preprocessUsages(project: Project, usages: Array<out UsageInfo>): Array<UsageInfo> {
        return usages.map2Array { it }
    }

    override fun prepareForDeletion(element: PsiElement) {
        prepareKotlinForDeletion(element)
    }

    override fun isToSearchInComments(element: PsiElement?): Boolean = searchesInComments(element)

    override fun setToSearchInComments(element: PsiElement?, enabled: Boolean) {
        setSearchesInComments(element, enabled)
    }

    override fun isToSearchForTextOccurrences(element: PsiElement?): Boolean = searchesForText(element)

    override fun setToSearchForTextOccurrences(element: PsiElement?, enabled: Boolean) {
        setSearchesForText(element, enabled)
    }
}

internal fun findKotlinUsages(
    element: PsiElement,
    allElementsToDelete: Array<out PsiElement>,
    result: MutableList<in UsageInfo>
): NonCodeUsageSearchInfo {

    fun isInside (t: PsiElement, ancestors : Array<out PsiElement>) : Boolean =
         ancestors.any { elementToDelete -> SafeDeleteProcessor.isInside(t, elementToDelete) } 
    
    val isInside: (t: PsiElement) -> Boolean = { isInside(it, allElementsToDelete) }

    val additionalElementsToDelete = arrayListOf<PsiElement>()
    if (element is KtNamedFunction || element is KtProperty) {
        findFunctionUsages(element as KtCallableDeclaration, allElementsToDelete, isInside, additionalElementsToDelete, result)
    }

    if (element is KtDeclaration) {
        val additionalElementsToDeleteArray = additionalElementsToDelete.toTypedArray()
        //group declarations into expected to receive conflicts once per expected/actuals group
        val expected = ExpectActualUtils.liftToExpect(element) ?: element
        ReferencesSearch.search(element).forEach(Processor {
            val e = it.element
            if (!isInside(e) && !isInside(e, additionalElementsToDeleteArray)) {
                if (e.getNonStrictParentOfType<KtValueArgumentName>() != null) {
                    //named argument would be deleted with argument
                    return@Processor true
                }
                val usageInfo = if (element is KtParameter && isPassThroughParameter(e, element)) {
                    SafeDeleteReferenceSimpleDeleteUsageInfo(e, expected, true)
                } else {
                    val importDirective = e.getNonStrictParentOfType<KtImportDirective>()
                    SafeDeleteReferenceSimpleDeleteUsageInfo(importDirective ?: e, expected, importDirective != null && importDirective.importedReference == e.parent)
                }
                result.add(usageInfo)
            }
            return@Processor true
        })
    }

    if (element is KtConstructor<*>) {
        element.processDelegationCallConstructorUsages(element.useScope()) { callElement ->
            result.add(SafeDeleteReferenceSimpleDeleteUsageInfo(callElement.calleeExpression ?: callElement, element, false))
            true
        }
    }

    if (element is KtTypeParameter) {
        val owner = element.getNonStrictParentOfType<KtTypeParameterListOwner>()
        if (owner != null) {
            val parameterList = owner.typeParameters
            val parameterIndex = parameterList.indexOf(element)
            for (reference in ReferencesSearch.search(owner).asIterable()) {
                JavaSafeDeleteDelegate.EP.forLanguage(reference.element.language)?.createJavaTypeParameterUsageInfo(
                    reference,
                    result,
                    element,
                    parameterList.size,
                    parameterIndex
                )
            }
        }
    }

    if (element is KtParameter) {
        if (element.isContextParameter) {
            findCallsWithContextParameters(result, element, element.ownerDeclaration)
        } else {
            val function = element.getNonStrictParentOfType<KtFunction>()
            if (function != null) {
                val parameterIndexAsJavaCall = element.parameterIndex() + if (function.receiverTypeReference != null) 1 else 0
                findCallArgumentsToDelete(result, element, parameterIndexAsJavaCall, function)
            }
        }
    }

    return NonCodeUsageSearchInfo(isInside, element)
}

private fun findCallsWithContextParameters(
    result: MutableList<in UsageInfo>,
    element: KtParameter,
    decl: KtDeclaration?
) {
    decl?.forEachDescendantOfType<KtExpression> { expression ->
        analyze(expression) {
            val declarationSymbol = decl.symbol as? KaCallableSymbol ?: return@forEachDescendantOfType
            val singleCall = expression.resolveSuccessfulExpressionCall()?.simple ?: return@forEachDescendantOfType
            if (expression is KtCallExpression && (singleCall !is KaImplicitInvokeCall)) return@forEachDescendantOfType
            if (declarationSymbol.allOverriddenSymbols.firstOrNull { it == singleCall.symbol } != null) return@forEachDescendantOfType
            if (singleCall.contextArguments.any { (it.unwrapSmartCasts() as? KaImplicitReceiverValue)?.symbol == element.symbol }) {
                result.add(SafeDeleteReferenceSimpleDeleteUsageInfo(expression, element, false))
            }
        }
    }
}

private fun findFunctionUsages(
    element: KtCallableDeclaration,
    allElementsToDelete: Array<out PsiElement>,
    isInside: (t: PsiElement) -> Boolean,
    additionalElementsToDelete: ArrayList<PsiElement>,
    result: MutableList<in UsageInfo>
) {
    val overridden = arrayListOf<PsiElement>()
    val containingClass = element.containingClass()
    if (containingClass != null) {
        overridden.add(element)
        element.findAllOverridings().forEach { m ->
            val original = m.unwrapped
            if (original != null && !allElementsToDelete.contains(original)) {
                analyze(original.getKaModule(original.project, useSiteModule = null)) {
                    val elementClassSymbol = containingClass.symbol as KaClassSymbol
                    val currentClassFqName = elementClassSymbol.classId?.asFqNameString()

                    fun isMultipleInheritance(functionSymbol: KaSymbol): Boolean {
                        val function = functionSymbol.psi
                        // need to process java separately as a workaround for KT-84737
                        if (function is PsiMethod && function.findSuperMethods().any { superMethod ->
                                !isInside(superMethod) &&
                                !(currentClassFqName != null && InheritanceUtil.isInheritor(superMethod.containingClass, currentClassFqName))
                            }) {
                            return true
                        }
                        val superMethods = (functionSymbol as? KaCallableSymbol)?.directlyOverriddenSymbols ?: return false
                        return superMethods.any {
                            val superClassSymbol = it.containingDeclaration as? KaClassSymbol ?: return@any false
                            val superMethod = it.psi ?: return@any false
                            return@any !isInside(superMethod) && !superClassSymbol.isSubClassOf(elementClassSymbol)
                        }
                    }

                    val oSymbol = when (original) {
                        is KtDeclaration -> original.symbol
                        is PsiMember -> original.callableSymbol
                        else -> null
                    }

                    if (oSymbol == null || !isMultipleInheritance(oSymbol)) {
                        overridden.add(original)
                    }
                }
            }
        }
    }

    for (overriddenFunction in overridden) {
        if (ReferencesSearch.search(overriddenFunction).forEach(Processor {
                val place = it.element
                return@Processor isInside(place)
            })) {
            additionalElementsToDelete.add(overriddenFunction)
            result.add(createParameterAwareDeleteUsageInfo(overriddenFunction, element))
        }
        else {
            JavaSafeDeleteDelegate.EP.forLanguage(overriddenFunction.language)?.createCleanupOverriding(overriddenFunction, allElementsToDelete, result)
        }
    }
}

private fun createParameterAwareDeleteUsageInfo(elementToDelete: PsiElement, referencedElement: PsiElement): SafeDeleteReferenceSimpleDeleteUsageInfo {
    if (elementToDelete !is KtParameter) {
        return SafeDeleteReferenceSimpleDeleteUsageInfo(elementToDelete, referencedElement, true)
    }

    return SafeDeleteParameterWithCommaUsageInfo(elementToDelete, referencedElement)
}

private fun findCallArgumentsToDelete(
    result: MutableList<in UsageInfo>,
    element: KtParameter,
    parameterIndexAsJavaCall: Int,
    ktElement: KtElement
) {
    if (ktElement is KtConstructor<*>) {
        val containingClass = ktElement.containingClass() ?: return

        val directInheritors = mutableListOf<KtElement>()
        DirectKotlinClassInheritorsSearch.search(containingClass).asIterable().forEach { el ->
            if (el !is KtElement) {
                val lightMethod = ktElement.toLightMethods().filterIsInstance<KtLightMethod>().firstOrNull() ?: return
                MethodReferencesSearch.search(lightMethod, lightMethod.useScope, true).forEach(Processor {
                    JavaSafeDeleteDelegate.EP.forLanguage(it.element.language)
                        ?.createUsageInfoForParameter(it, result, element, parameterIndexAsJavaCall, element.isVarArg)
                    return@Processor true
                })
                return
            }
            directInheritors.add(el)
        }

        fun processDelegatingReferences(ktClass: KtClass) {
            ktClass.secondaryConstructors.forEach { c ->
                if (c != ktElement) {
                    val delegationCall = c.getDelegationCallOrNull()
                    val reference = delegationCall?.calleeExpression?.mainReference
                    if (reference != null && reference.resolve() == ktElement) {
                        JavaSafeDeleteDelegate.EP.forLanguage(reference.element.language)
                            ?.createUsageInfoForParameter(reference, result, element, parameterIndexAsJavaCall, element.isVarArg)
                    }
                }
            }
        }

        processDelegatingReferences(containingClass)
        for (inheritor in directInheritors) {
            (inheritor as? KtClass)?.let { processDelegatingReferences(it) }
        }

        //to find constructor calls in java, one needs to perform class search
        findCallArgumentsToDelete(result, element, parameterIndexAsJavaCall, containingClass)
    }


    ReferencesSearch.search(ktElement).forEach(Processor {
        JavaSafeDeleteDelegate.EP.forLanguage(it.element.language)
            ?.createUsageInfoForParameter(it, result, element, parameterIndexAsJavaCall, element.isVarArg)
        return@Processor true
    })
}

private fun isPassThroughParameter(
    refElement: PsiElement,
    parameter: KtParameter
): Boolean {
    val function = parameter.ownerFunction ?: return false

    val callExpr = refElement.getNonStrictParentOfType<KtCallExpression>() ?: return false
    val calleeExpression = callExpr.calleeExpression
    if (calleeExpression?.text != function.name) return false

    analyze(callExpr) {
        val resolvedCall = callExpr.resolveSuccessfulCall() ?: return false

        if (resolvedCall.symbol != function.symbol) {
            return false
        }

        val variableSignature = resolvedCall.valueArgumentMapping[refElement]
        return variableSignature?.symbol == parameter.symbol
    }
}

/**
 * @return the text of the question whether to delete the expected declaration, or the parameter in it, together with the actual one;
 * null if [element] is not an actual declaration
 */
private fun expectActualNote(element: PsiElement): @Nls String? = when {
    element !is KtDeclaration -> null
    element.hasActualModifier() -> KotlinBundle.message("do.you.want.to.delete.expected.declaration.together.with.all.related.actual.ones")
    element is KtParameter && element.ownerFunction?.hasActualModifier() == true ->
        KotlinBundle.message("do.you.want.to.delete.this.parameter.in.expected.declaration.and.all.related.actual.ones")
    else -> null
}

/**
 * The elements to search for the deletion of [element], after the questions whether to delete the base declarations of a function or a
 * property, a parameter in the whole hierarchy, and the expected declaration together with the actual one, see
 * [ModPlanKotlinSafeDeleteOptions.DELETE_SUPER_DECLARATIONS], [ModPlanSafeDeleteOptions.DELETE_HIERARCHY] and
 * [ModPlanKotlinSafeDeleteOptions.DELETE_EXPECT_ACTUAL].
 *
 * The provider and the legacy `getElementsToSearch` share this code: the provider goes on with the elements in [ModPlanChain.andThen],
 * and the legacy flow ends with them.
 *
 * @return the chain that ends with the elements to search; empty if there is nothing to search
 */
internal fun kotlinElementsToSearch(
    chainBuilder: ModPlanChainBuilder,
    element: PsiElement,
    allElementsToDelete: Collection<PsiElement>,
): ModPlanChain<List<PsiElement>> =
    declarationsToSearch(chainBuilder, element, allElementsToDelete).andThen { afterDeclarations, declarations ->
        withExpectedActuals(afterDeclarations, declarations)
    }

/** @return the chain that ends with [element], its hierarchy or its base declarations, as the questions about them answer */
private fun declarationsToSearch(
    chainBuilder: ModPlanChainBuilder,
    element: PsiElement,
    allElementsToDelete: Collection<PsiElement>,
): ModPlanChain<List<PsiElement>> = when (element) {
    is KtParameter -> {
        val parametersToSearch = parameterHierarchy(element)
        if (parametersToSearch.size <= 1) {
            chainBuilder.passValue(parametersToSearch)
        }
        else {
            val parameterSubjects = parametersToSearch.map { SymbolPointer.of(it) }
            val elementSubjects = listOf(SymbolPointer.of(element))
            val hierarchyQuestion = parameterHierarchyQuestion(element)
            chainBuilder.ask(hierarchyQuestion) { answered ->
                val deletesHierarchy = hierarchyQuestion.isConfirmed(answered.request.optionValues())
                val searchedSubjects = if (deletesHierarchy) { parameterSubjects } else { elementSubjects }
                answered.passValue(ModPlanSafeDeleteUsageInfoUtil.elementsOf(searchedSubjects).toList())
            }
        }
    }

    is KtNamedFunction, is KtProperty -> {
        val declaration = element as KtDeclaration
        val overridden = overriddenDeclarations(declaration, allElementsToDelete)
        if (overridden.isEmpty()) {
            chainBuilder.passValue(listOf(element))
        }
        else {
            val superDeclarationsQuestion = KotlinSafeDeleteSuperDeclarationsQuestion(declaration)
            val declarationSubject = SymbolPointer.of(declaration)
            val overriddenSubjects = overridden.map { SymbolPointer.of(it) }
            chainBuilder.ask(superDeclarationsQuestion) { answered ->
                val currentDeclaration = declarationSubject.element() as? KtDeclaration ?: return@ask answered.passValue(emptyList())
                if (superDeclarationsQuestion.shouldDeleteSuperDeclarations(answered.request.optionValues())) {
                    answered.passValue(ModPlanSafeDeleteUsageInfoUtil.elementsOf(overriddenSubjects).toList())
                }
                else {
                    answered.passValue(listOf(currentDeclaration))
                }
            }
        }
    }

    else -> chainBuilder.passValue(listOf(element))
}

/**
 * Asks whether to delete the expected declaration of an actual one, or the parameter in it.
 *
 * @return the chain that ends with [elements] and their expected and actual declarations
 */
private fun withExpectedActuals(
    chainBuilder: ModPlanChainBuilder,
    elements: List<PsiElement>,
): ModPlanChain<List<PsiElement>> {
    val expectActualMessage = elements.firstNotNullOfOrNull { expectActualNote(it) }
    val expectActualQuestion = if (expectActualMessage == null) {
        null
    }
    else {
        KotlinSafeDeleteConfirmationQuestion(ModPlanKotlinSafeDeleteOptions.DELETE_EXPECT_ACTUAL) { _ ->
            Messages.showYesNoDialog(expectActualMessage, RefactoringBundle.message("safe.delete.title"), Messages.getQuestionIcon()) ==
                Messages.YES
        }
    }
    val elementSubjects = elements.map { SymbolPointer.of(it) }
    return chainBuilder.ask(expectActualQuestion) { answered ->
        val deleteExpectActual = answered.request.optionValues().get(ModPlanKotlinSafeDeleteOptions.DELETE_EXPECT_ACTUAL)
        answered.passValue(ModPlanSafeDeleteUsageInfoUtil.elementsOf(elementSubjects).flatMap { declaration ->
            if (declaration is KtDeclaration && (deleteExpectActual || expectActualNote(declaration) == null)) {
                ExpectActualUtils.withExpectedActuals(declaration)
            }
            else {
                listOf(declaration)
            }
        })
    }
}

/**
 * @param headless true analyzes on the current background thread, instead of under a modal progress on EDT.
 */
internal fun collectKotlinConflicts(element: PsiElement, conflicts: MultiMap<PsiElement, @NlsContexts.DialogMessage String>, headless: Boolean) {
    if (element is KtNamedFunction || element is KtProperty) {
        val ktClass = element.getNonStrictParentOfType<KtClass>()
        if (ktClass == null || ktClass.body != element.parent) return

        val modifierList = ktClass.modifierList
        if (modifierList != null && modifierList.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return

        val declaration = element as KtDeclaration
        if (headless) {
            analyze(declaration) { collectImplementsConflicts(declaration, conflicts) }
        } else {
            analyzeInModalWindow(declaration, RefactoringBundle.message("detecting.possible.conflicts")) {
                collectImplementsConflicts(declaration, conflicts)
            }
        }
    }
}

context(_: KaSession)
private fun collectImplementsConflicts(element: KtDeclaration, conflicts: MultiMap<PsiElement, @NlsContexts.DialogMessage String>) {
    (element.symbol as? KaCallableSymbol)?.allOverriddenSymbols
        ?.filter { it.modality == KaSymbolModality.ABSTRACT }
        ?.mapNotNull { it.psi }
        ?.forEach {
            val message = KotlinK2RefactoringsBundle.message("safe.delete.implements.conflict.message",
                ElementDescriptionUtil.getElementDescription(element, RefactoringDescriptionLocation.WITH_PARENT),
                ElementDescriptionUtil.getElementDescription(it, RefactoringDescriptionLocation.WITH_PARENT)
            )
            conflicts.putValue(it, StringUtil.capitalize(message))
        }
}

/** Drops `actual` as [prepareKotlinForDeletion] does after the user refused to delete the parameter in the expected declaration. */
internal fun prepareKotlinForDeletion(element: PsiElement, deleteExpectActual: Boolean) {
    if (element is KtParameter && !element.hasActualModifier() && !deleteExpectActual) {
        val function = element.ownerFunction
        if (function != null && function.hasActualModifier()) {
            function.removeModifierKeyword(KtTokens.ACTUAL_KEYWORD)
        }
    }
    prepareKotlinForDeletion(element)
}

internal fun prepareKotlinForDeletion(element: PsiElement) {
    when (element) {
        is KtTypeParameter -> {
            deleteSeparatingComma(element)
            deleteBracesAroundEmptyList(element)
        }

        is KtProperty if ((element.parent as? KtWhenExpression)?.subjectVariable === element) -> {
            val commentSaver = CommentSaver(element)
            element.initializer?.let { initializer ->
                val replaced = element.replace(initializer.copy())
                commentSaver.restore(replaced)
            }
        }

        is KtParameter -> {
            element.ownerFunction?.let {
                with(KotlinSafeDeleteSettings) {
                    if (it.dropActualModifier == true) {
                        it.removeModifierKeyword(KtTokens.ACTUAL_KEYWORD)
                        it.dropActualModifier = null
                    }
                }
            }
            deleteSeparatingComma(element)
            if (element.isContextParameter) {
                val contextParameterList = element.parent as KtContextParameterList
                if (contextParameterList.contextParameters.size == 1) {
                    contextParameterList.delete()
                }
            }
        }
    }
}

internal fun searchesInComments(element: PsiElement?): Boolean {
    val settings = KotlinFirRefactoringsSettings.instance
    when (element) {
        is KtClass -> return settings.RENAME_SEARCH_IN_COMMENTS_FOR_CLASS
        is KtFunction -> return settings.RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION
        is KtProperty -> return settings.RENAME_SEARCH_IN_COMMENTS_FOR_PROPERTY
        else -> return false
    }
}

internal fun setSearchesInComments(element: PsiElement?, enabled: Boolean) {
    val settings = KotlinFirRefactoringsSettings.instance
    when (element) {
        is KtClass -> settings.RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = enabled
        is KtFunction -> settings.RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION = enabled
        is KtProperty -> settings.RENAME_SEARCH_IN_COMMENTS_FOR_PROPERTY = enabled
    }
}

internal fun searchesForText(element: PsiElement?): Boolean {
    val settings = KotlinFirRefactoringsSettings.instance
    when (element) {
        is KtClass -> return settings.RENAME_SEARCH_FOR_TEXT_FOR_CLASS
        is KtFunction -> return settings.RENAME_SEARCH_FOR_TEXT_FOR_FUNCTION
        is KtProperty -> return settings.RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE
        else -> return false
    }
}

internal fun setSearchesForText(element: PsiElement?, enabled: Boolean) {
    val settings = KotlinFirRefactoringsSettings.instance
    when (element) {
        is KtClass -> settings.RENAME_SEARCH_FOR_TEXT_FOR_CLASS = enabled
        is KtFunction -> settings.RENAME_SEARCH_FOR_TEXT_FOR_FUNCTION = enabled
        is KtProperty -> settings.RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE = enabled
    }
}

/** Deletes a parameter of an overriding function with its separating comma. */
private class SafeDeleteParameterWithCommaUsageInfo(
    parameter: KtParameter,
    referencedElement: PsiElement,
) : SafeDeleteReferenceSimpleDeleteUsageInfo(parameter, referencedElement, true) {
    override fun deleteElement() {
        val parameter = element as? KtParameter ?: return
        deleteParameterWithComma(parameter)
    }

    override fun asModPlanEdit(): ModPlanEdit? {
        val parameter = element as? KtParameter ?: return null
        if (findReferencedElement() == null) return null
        return ModPlanEdit.update(parameter, ::deleteParameterWithComma)
    }
}

private fun deleteParameterWithComma(parameter: KtParameter) {
    deleteSeparatingComma(parameter)
    parameter.delete()
}
