// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.Ref
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.FunctionalExpressionSearch
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.rename.ResolveSnapshotProvider
import com.intellij.refactoring.rename.ResolveSnapshotProvider.ResolveSnapshot
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.isEffectivelyActual
import org.jetbrains.kotlin.idea.base.util.useScope
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.*
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.changeSignature.setValOrVar
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinRenameRefactoringSupport
import org.jetbrains.kotlin.idea.refactoring.replaceListPsiAndKeepDelimiters
import org.jetbrains.kotlin.idea.search.ExpectActualUtils
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isOverridable
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchOptions
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchParameters
import org.jetbrains.kotlin.idea.search.usagesSearch.processDelegationCallConstructorUsages
import org.jetbrains.kotlin.idea.searching.inheritors.findAllOverridings
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.addRemoveModifier.setModifierList
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.expressions.OperatorConventions

private val primaryElementsKey = Key.create<List<KtNamedDeclaration>>("expectActual")

class KotlinChangeSignatureUsageProcessor : ChangeSignatureUsageProcessor {
    override fun findUsages(changeInfo: ChangeInfo): Array<UsageInfo> {
        if (changeInfo is JavaChangeInfo) {
            val psiMethod = changeInfo.method
            val containingClass = psiMethod.containingClass
            if (containingClass != null && LambdaUtil.isFunctionalClass(containingClass)) {
                return ReferencesSearch.search(containingClass).asIterable().mapNotNull {ref ->
                    val ktElement = ref.element as? KtElement ?: return@mapNotNull null
                    val ktCallExpression = ktElement.parent as? KtCallExpression ?: return@mapNotNull null
                    if (ktCallExpression.calleeExpression == ktElement && ktCallExpression.lambdaArguments.size == 1) {
                        val overrider = ktCallExpression.lambdaArguments[0].getLambdaExpression()?.functionLiteral
                        overrider?.let { KotlinOverrideUsageInfo(overrider, psiMethod, false) }
                    } else null
                }.toTypedArray()
            }
        }

        if (changeInfo !is KotlinChangeInfo) return emptyArray()

        val result = mutableListOf<UsageInfo>()
        val ktCallableDeclaration = changeInfo.method

        if (ktCallableDeclaration is KtNamedFunction && !ktCallableDeclaration.hasBody()) {
            ktCallableDeclaration.toLightMethods().forEach { lightMethod ->
                FunctionalExpressionSearch.search(lightMethod).asIterable().forEach { functionalExpression ->
                    val provider = ChangeSignatureUsageProviders.findProvider(functionalExpression.language)
                    if (provider != null) {
                        val usageInfo = provider.createOverrideUsageInfo(
                            changeInfo,
                            functionalExpression,
                            lightMethod,
                            true,
                            true,
                            true,
                            result
                        )
                        usageInfo?.let { result.add(it) }
                    }
                }
            }
        }

        val primaryElements = mutableListOf<KtNamedDeclaration>()
        changeInfo.putUserData(
            primaryElementsKey,
            primaryElements
        )
        ExpectActualUtils.withExpectedActuals(ktCallableDeclaration).forEach { ktCallableDeclaration ->
            if (ktCallableDeclaration is KtCallableDeclaration) {
                KotlinChangeSignatureUsageSearcher.findInternalUsages(ktCallableDeclaration, changeInfo, result)
            }

            if (ktCallableDeclaration is KtPrimaryConstructor) {
                findConstructorPropertyUsages(ktCallableDeclaration, changeInfo, result)
            }

            if (ktCallableDeclaration is KtNamedDeclaration) {
                primaryElements.add(ktCallableDeclaration)
                findUsages(ktCallableDeclaration, changeInfo, result)
            }
        }

        if (ktCallableDeclaration is KtClass && ktCallableDeclaration.isEnum()) {
            for (declaration in ktCallableDeclaration.declarations) {
                if (declaration is KtEnumEntry && declaration.superTypeListEntries.isEmpty()) {
                    result.add(KotlinEnumEntryWithoutSuperCallUsage(declaration))
                }
            }
        }

        ktCallableDeclaration.processDelegationCallConstructorUsages(ktCallableDeclaration.useScope()) { callElement ->
            when (callElement) {
                is KtConstructorDelegationCall -> result.add(KotlinConstructorDelegationCallUsage(callElement, ktCallableDeclaration))
                is KtSuperTypeCallEntry -> result.add(KotlinFunctionCallUsage(callElement, ktCallableDeclaration))
            }
            true
        }

        return result.groupBy { it.javaClass }
            .values
            .flatMap { it.sortedWith { u1, u2 -> PsiUtilCore.compareElementsByPosition(u1.element, u2.element) } }
            .toTypedArray()
    }

    private fun findUsages(
        ktCallableDeclaration: KtNamedDeclaration,
        changeInfo: ChangeInfo,
        result: MutableList<UsageInfo>
    ) {
        (ktCallableDeclaration as? KtCallableDeclaration)?.findAllOverridings()?.forEach { overrider ->
            val provider = ChangeSignatureUsageProviders.findProvider(overrider.language)
            if (provider != null) {
                val usageInfo = provider.createOverrideUsageInfo(
                    changeInfo,
                    overrider,
                    ktCallableDeclaration,
                    true,
                    true,
                    true,
                    result
                )
                usageInfo?.let { result.add(it) }
            }

            if (overrider is KtCallableDeclaration && changeInfo is KotlinChangeInfoBase) {
                KotlinChangeSignatureUsageSearcher.findInternalUsages(overrider, changeInfo, result)
            }
        }

        findReferences(ktCallableDeclaration).forEach { reference ->
            val provider = ChangeSignatureUsageProviders.findProvider(reference.element.language)
            if (provider != null) {
                val usageInfo = provider.createUsageInfo(changeInfo, reference, ktCallableDeclaration, true, true)
                usageInfo?.let { result.add(it) }
            }
        }
    }

    private fun findConstructorPropertyUsages(
        primaryConstructor: KtPrimaryConstructor,
        changeInfo: KotlinChangeInfoBase,
        result: MutableList<UsageInfo>
    ) {
        for ((index, parameter) in primaryConstructor.valueParameters.withIndex()) {
            if (!parameter.isOverridable()) continue

            changeInfo.newParameters.find { it.originalIndex == index } ?: continue

            val propertyUsageInfo = createChangeInfo(changeInfo, parameter)
            findUsages(parameter, propertyUsageInfo, result)
        }
    }

    /* should be the same for rename refactoring */
    private fun findReferences(psi: PsiElement): Set<PsiReference> {
        val result = LinkedHashSet<PsiReference>()
        val options = KotlinReferencesSearchOptions(
            acceptCallableOverrides = true,
            acceptOverloads = false,
            acceptExtensionsOfDeclarationClass = false,
            acceptCompanionObjectMembers = false
        )

        val parameters = KotlinReferencesSearchParameters(psi, psi.useScope, false, null, options)
        result.addAll(ReferencesSearch.search(parameters).findAll())
        if (psi is KtProperty && !psi.isLocal || psi is KtParameter && psi.hasValOrVar() || psi is KtConstructor<*>) {
            psi.toLightMethods().flatMapTo(result) { MethodReferencesSearch.search(it, psi.useScope, true).findAll() }
        }

        return result
    }

    override fun findConflicts(
        info: ChangeInfo,
        refUsages: Ref<Array<UsageInfo>>
    ): MultiMap<PsiElement, @DialogMessage String> {
        if (info is KotlinChangeInfo) {
            return KotlinChangeSignatureConflictSearcher(info, refUsages).findConflicts()
        }
        return MultiMap<PsiElement, String>()
    }

    override fun processUsage(
        changeInfo: ChangeInfo,
        usageInfo: UsageInfo,
        beforeMethodChange: Boolean,
        usages: Array<out UsageInfo>
    ): Boolean {
        val element = usageInfo.element?.unwrapped ?: return false
        if (!element.language.`is`(KotlinLanguage.INSTANCE)) return false
        val kotlinChangeInfo = fromJavaChangeInfo(changeInfo, usageInfo, beforeMethodChange) ?: return false
        if (!beforeMethodChange) {
            if (usageInfo is KotlinBaseChangeSignatureUsage) {
                usageInfo.processUsage(kotlinChangeInfo, element as KtElement, usages)?.let { shortenReferences(it, ShortenOptions.ALL_ENABLED) }
            }
        }
        else {
            if (usageInfo is KotlinByConventionCallUsage) {
                usageInfo.preprocessUsage()
            }
            if (usageInfo is KotlinEnumEntryWithoutSuperCallUsage) {
                usageInfo.preprocess(kotlinChangeInfo, usageInfo.element as KtElement)
            }
            if (usageInfo is KotlinOverrideUsageInfo && element is KtCallableDeclaration) {
                val baseElement = usageInfo.baseMethod
                val mappedChangeInfo =
                    (kotlinChangeInfo as? KotlinChangeInfo)?.dependentProperties?.get(baseElement)
                        ?: kotlinChangeInfo
                updatePrimaryMethod(element, mappedChangeInfo, isInherited = true, isCaller = usageInfo.isCaller)
            }
            if (usageInfo is CallerUsageInfo && element is KtNamedDeclaration) {
                updatePrimaryMethod(element, kotlinChangeInfo, isCaller = true)
            }
        }

        return false
    }

    private fun createChangeInfo(kotlinChangeInfo: KotlinChangeInfoBase, element: PsiElement?): KotlinChangeInfoBase {
        val method = kotlinChangeInfo.method
        if ((element is KtParameter || element is KtProperty) && method is KtPrimaryConstructor) {
            element as KtCallableDeclaration
            val parameterInfo = kotlinChangeInfo.newParameters.find { it.oldName == element.name }!!
            val propertyChangeInfo = KotlinChangeInfo(
                KotlinMethodDescriptor(element),
                name = parameterInfo.name,
                newReturnTypeInfo = parameterInfo.currentType,
            )
            (kotlinChangeInfo as KotlinChangeInfo).registerPropertyChangeInfo(element, propertyChangeInfo)
            return propertyChangeInfo
        }
        return kotlinChangeInfo
    }

    override fun processPrimaryMethod(changeInfo: ChangeInfo): Boolean {
        val unwrapped = changeInfo.method.unwrapped
        val kotlinChangeInfo =
            (if (changeInfo !is KotlinChangeInfo && unwrapped is KtNamedDeclaration) {
                fromJavaChangeInfo(changeInfo, UsageInfo(unwrapped), true)
            } else changeInfo) as? KotlinChangeInfoBase ?: return false
        val element = unwrapped as? KtNamedDeclaration ?: return false

        val namedDeclarations = (kotlinChangeInfo as? KotlinChangeInfo)?.getUserData(primaryElementsKey)?.reversed() ?: listOf(element)
        val refactoringSupport = KotlinRenameRefactoringSupport.getInstance()
        for (declaration in namedDeclarations) {
            updatePrimaryMethod(declaration, kotlinChangeInfo)
            refactoringSupport.dropOverrideKeywordIfNecessary(declaration)
        }
        return true
    }

    @OptIn(KaExperimentalApi::class, KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
    fun updatePrimaryMethod(
        element: KtNamedDeclaration,
        changeInfo: KotlinChangeInfoBase,
        isInherited: Boolean = false,
        isCaller: Boolean = false
    ) {
        val psiFactory = KtPsiFactory(element.project)

        if (changeInfo.isNameChanged) {
            //operator modifier is auto-removed if new name doesn't correspond to the convention
            if (element.nameIdentifier != null) {
                element.setName(changeInfo.newName)
            }
            else if (isReturnTypeRequired(element)) {
                element.add(psiFactory.createIdentifier(changeInfo.newName))
            }
        }

        changeReturnTypeIfNeeded(changeInfo, element)

        if (changeInfo.isParameterSetOrOrderChanged) {
            processParameterListWithStructuralChanges(changeInfo, element, (element as? KtCallableDeclaration)?.valueParameterList, psiFactory, changeInfo.method, isInherited, isCaller)
        }
        else {
            val parameterList = (element as? KtCallableDeclaration)?.valueParameterList
            if (parameterList != null) {
                val offset = if (element.receiverTypeReference != null) 1 else 0
                val parameterTypes = mutableMapOf<KtParameter, KtTypeReference>()
                for ((paramIndex, parameter) in parameterList.parameters.withIndex()) {
                    val parameterInfo = changeInfo.newParameters[paramIndex + offset]
                    if (!(element.isEffectivelyActual() && changeInfo.method is KtNamedDeclaration && (changeInfo.method as KtNamedDeclaration).isExpectDeclaration())) {
                        parameter.setValOrVar(if (element.isExpectDeclaration()) KotlinValVar.None else parameterInfo.valOrVar)
                    }
                    if (parameter.typeReference != null || isReturnTypeRequired(element)) {
                        parameterTypes[parameter] =
                            psiFactory.createType(parameterInfo.typeText, element, changeInfo.method, Variance.IN_VARIANCE)
                    }
                    val inheritedName = parameterInfo.getInheritedName(element.takeIf { isInherited })
                    if (Name.isValidIdentifier(inheritedName)) {
                        val newIdentifier = psiFactory.createIdentifier(inheritedName)
                        val nameIdentifier = parameter.nameIdentifier
                        if (nameIdentifier != null) {
                            nameIdentifier.replace(newIdentifier)
                        } else if (parameter.getDestructuringDeclaration() == null) {
                            parameter.add(newIdentifier)
                        }
                    }
                }
                //update all types together not to break inference during `createType` for dependent type changes
                parameterTypes.forEach { (param, typeRef) -> param.typeReference = typeRef }
            }
        }

        if (changeInfo.isReceiverTypeChanged()) {
            val receiverTypeText = changeInfo.receiverParameterInfo?.typeText
            val receiverTypeRef = if (receiverTypeText != null) psiFactory.createType(
                receiverTypeText,
                element as KtCallableDeclaration,
                changeInfo.method,
                Variance.IN_VARIANCE,
                true
            ) else null
            (element as KtCallableDeclaration).setReceiverTypeReference(receiverTypeRef)?.let { shortenReferences(it) }
        }

        if (changeInfo.isVisibilityChanged() && !KtPsiUtil.isLocal(element)) {
            changeVisibility(changeInfo, element)
        }

        val newName = changeInfo.newName
        if (newName != null && OperatorConventions.isConventionName(Name.identifier(newName)) && element.hasModifier(KtTokens.OPERATOR_KEYWORD)) {
            val brokenSignature = allowAnalysisFromWriteAction {
                allowAnalysisOnEdt {
                    analyze(element) {
                        element.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                            .any { it.diagnosticClass == KaFirDiagnostic.InapplicableOperatorModifier::class }
                    }
                }
            }
            if (brokenSignature) {
                element.removeModifier(KtTokens.OPERATOR_KEYWORD)
            }
        }

        val parameterList = (element as? KtCallableDeclaration)?.valueParameterList
        if (parameterList != null) {
            shortenReferences(parameterList)
        }
    }

    private fun changeVisibility(changeInfo: KotlinChangeInfoBase, element: KtElement) {
        val newVisibilityToken = when (changeInfo.aNewVisibility) {
            Visibilities.Private -> KtTokens.PRIVATE_KEYWORD
            Visibilities.Public -> KtTokens.PUBLIC_KEYWORD
            Visibilities.Protected -> KtTokens.PROTECTED_KEYWORD
            Visibilities.Internal -> KtTokens.INTERNAL_KEYWORD
            else -> return
        }
        when (element) {
            is KtCallableDeclaration -> element.setVisibility(newVisibilityToken)
            is KtClass -> element.createPrimaryConstructorIfAbsent().setVisibility(newVisibilityToken)
            else -> throw AssertionError("Invalid element: " + element.getElementTextWithContext())
        }
    }

    private fun KtModifierListOwner.setVisibility(visibilityToken: KtModifierKeywordToken) {
        if (visibilityToken == KtTokens.PUBLIC_KEYWORD) {
            visibilityModifierType()?.let { removeModifier(it) }
        } else {
            addModifier(visibilityToken)
        }
    }

    private fun processParameterListWithStructuralChanges(
        changeInfo: KotlinChangeInfoBase,
        element: KtDeclaration,
        originalParameterList: KtParameterList?,
        psiFactory: KtPsiFactory,
        baseFunction: PsiElement,
        isInherited: Boolean,
        isCaller: Boolean
    ) {
        var parameterList = originalParameterList
        val parametersCount = changeInfo.newParameters.count { it != changeInfo.receiverParameterInfo && !it.isContextParameter }
        val isLambda = element is KtFunctionLiteral
        var canReplaceEntireList = false

        if (!isCaller && !isLambda) {
            val contextParams = changeInfo.newParameters.filter { it.isContextParameter }
            updateContextParametersList(contextParams, element, baseFunction, isInherited, psiFactory)
        }

        var newParameterList: KtParameterList? = null
        if (isLambda) {
            if (parametersCount == 0) {
                if (parameterList != null) {
                    parameterList.delete()
                    element.arrow?.delete()
                    parameterList = null
                }
            } else {
                newParameterList = psiFactory.createLambdaParameterList(changeInfo.getNewParametersSignatureWithoutParentheses(element, baseFunction, isInherited))
                canReplaceEntireList = true
            }
        } else if (!(element is KtProperty || element is KtParameter)) {
            if (isCaller) {
                //propagation
                val paramString = buildString {
                    val existingParameters = (element as? KtCallableDeclaration)?.valueParameters?.joinToString(", ") {
                        it.text
                    }
                    if (existingParameters != null) {
                        append(existingParameters)
                    }
                    val newParameters = changeInfo.newParameters.filter { it.isNewParameter }
                    if (isNotEmpty() && newParameters.isNotEmpty()) {
                        append(",")
                    }
                    append(newParameters.joinToString(", ") {
                        it.getDeclarationSignature(element, baseFunction, false).text
                    })
                }

                newParameterList = psiFactory.createParameterList("($paramString)")
            } else {
                newParameterList = psiFactory.createParameterList("(" + changeInfo.getNewParametersSignatureWithoutParentheses(element as? KtCallableDeclaration, baseFunction, isInherited) + ")")
            }
        }

        if (newParameterList == null) return

        if (parameterList != null) {
            newParameterList = if (canReplaceEntireList) {
                parameterList.replace(newParameterList) as KtParameterList
            } else {
                //adjustTrailingComments(parameterList, newParameterList, psiFactory)
                replaceListPsiAndKeepDelimiters(changeInfo, parameterList, newParameterList) { parameters }
            }
        } else {
            if (element is KtClass) {
                val constructor = element.createPrimaryConstructorIfAbsent()
                if (element.hasActualModifier()) {
                    constructor.addModifier(KtTokens.ACTUAL_KEYWORD)
                }
                val oldParameterList = constructor.valueParameterList ?: error("Primary constructor has to have parameter list")
                newParameterList = oldParameterList.replace(newParameterList) as KtParameterList
            } else if (isLambda) {
                val functionLiteral = element
                val anchor = functionLiteral.lBrace
                newParameterList = element.addAfter(newParameterList, anchor) as KtParameterList
                if (functionLiteral.arrow == null) {
                    val whitespaceAndArrow = psiFactory.createWhitespaceAndArrow()
                    element.addRangeAfter(whitespaceAndArrow.first, whitespaceAndArrow.second, newParameterList)
                }
            }
        }

        shortenReferences(newParameterList)
    }

    private fun updateContextParametersList(
        contextParams: List<KotlinParameterInfo>,
        element: KtDeclaration,
        baseFunction: PsiElement,
        isInherited: Boolean,
        psiFactory: KtPsiFactory
    ) {
        if (contextParams.isNotEmpty()) {
            val list = contextParams.joinToString { it.getDeclarationSignature(element, baseFunction, isInherited).text }
            val newModifierList = psiFactory.createFunction("context($list) fun test() {}").modifierList!!
            val modifierList = element.modifierList
            if (modifierList != null) {
                val contextReceiverList = modifierList.contextReceiverList
                if (contextReceiverList == null) {
                    modifierList.add(newModifierList.contextReceiverList!!)
                } else {
                    contextReceiverList.replace(newModifierList.contextReceiverList!!)
                }
            } else {
                element.setModifierList(newModifierList)
            }
        } else {
            (element as? KtTypeParameterListOwnerStub<*>)?.contextReceiverList?.delete()
        }
    }

    private fun isReturnTypeRequired(element: KtNamedDeclaration): Boolean {
        return (element is KtFunction && element !is KtFunctionLiteral) || element is KtProperty || element is KtParameter
    }

    private fun changeReturnTypeIfNeeded(changeInfo: KotlinChangeInfoBase, element: KtNamedDeclaration) {
        if (element !is KtCallableDeclaration) return
        if (element is KtConstructor<*>) return

        val returnTypeIsNeeded = isReturnTypeRequired(element)

        if (changeInfo.isReturnTypeChanged && returnTypeIsNeeded) {
            element.typeReference = null
            val returnType = changeInfo.aNewReturnType
            if (returnType != null) {
                val typeReference = KtPsiFactory(element.project).createType(
                    returnType,
                    element,
                    changeInfo.method,
                    Variance.OUT_VARIANCE
                )
                element.setTypeReference(typeReference)?.let { shortenReferences(it) }
            }
        }
    }

    override fun shouldPreviewUsages(
        changeInfo: ChangeInfo,
        usages: Array<UsageInfo>
    ): Boolean {
        return false
    }

    override fun setupDefaultValues(
        changeInfo: ChangeInfo,
        refUsages: Ref<Array<UsageInfo>?>,
        project: Project
    ): Boolean {
        return true
    }

    override fun registerConflictResolvers(
        snapshots: MutableList<in ResolveSnapshot>,
        resolveSnapshotProvider: ResolveSnapshotProvider,
        usages: Array<out UsageInfo>,
        changeInfo: ChangeInfo
    ) {}
}