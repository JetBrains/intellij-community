// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.searching.usages

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.MethodSignatureUtil
import org.jetbrains.kotlin.analysis.api.*
import org.jetbrains.kotlin.analysis.api.calls.KtDelegatedConstructorCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeMappingMode
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.references.unwrappedTargets
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.Companion.isInheritable
import org.jetbrains.kotlin.idea.search.ReceiverTypeSearcherInfo
import org.jetbrains.kotlin.idea.stubindex.KotlinTypeAliasShortNameIndex
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.DataClassResolver
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.util.match

internal class KotlinK2SearchUsagesSupport : KotlinSearchUsagesSupport {
    override fun isInvokeOfCompanionObject(psiReference: PsiReference, searchTarget: KtNamedDeclaration): Boolean {

        if (searchTarget is KtObjectDeclaration && searchTarget.isCompanion()) {
            val invokeOperatorCandidate = psiReference.resolve() ?: return false
            if (invokeOperatorCandidate is KtNamedFunction && invokeOperatorCandidate.name == OperatorNameConventions.INVOKE.asString()) {
                analyze(searchTarget) {
                    val searchTargetContainerSymbol = searchTarget.getSymbol() as? KtClassOrObjectSymbol ?: return false
                    val invokeSymbol = invokeOperatorCandidate.getSymbol() as? KtFunctionSymbol ?: return false

                    fun KtClassOrObjectSymbol.isInheritorOrSelf(
                        superSymbol: KtClassOrObjectSymbol?
                    ): Boolean {
                        if (superSymbol == null) return false
                        return superSymbol == this || isSubClassOf(superSymbol)
                    }

                    return searchTargetContainerSymbol.isInheritorOrSelf(invokeSymbol.getContainingSymbol() as? KtClassOrObjectSymbol) ||
                            searchTargetContainerSymbol.isInheritorOrSelf(invokeSymbol.receiverParameter?.type?.expandedClassSymbol)
                }
            }
        }

        return false
    }


    override fun actualsForExpected(declaration: KtDeclaration, module: Module?): Set<KtDeclaration> {
        return emptySet()
    }

    override fun expectedDeclarationIfAny(declaration: KtDeclaration): KtDeclaration? {
        return null
    }

    override fun isExpectDeclaration(declaration: KtDeclaration): Boolean {
        return false
    }

    override fun dataClassComponentMethodName(element: KtParameter): String? {
        if (!element.hasValOrVar() || element.containingClassOrObject?.hasModifier(KtTokens.DATA_KEYWORD) != true) return null
        analyze(element) {
            val paramSymbol = element.getSymbol() as? KtValueParameterSymbol ?: return null
            val constructorSymbol = paramSymbol.getContainingSymbol() as? KtConstructorSymbol ?: return null
            val index = constructorSymbol.valueParameters.indexOf(paramSymbol)
            return DataClassResolver.createComponentName(index + 1).asString()
        }
    }

    override fun hasType(element: KtExpression): Boolean {
        return analyze(element) {
            element.getKtType() != null
        }
    }

    override fun isCallableOverride(subDeclaration: KtDeclaration, superDeclaration: PsiNamedElement): Boolean {
        return analyze(subDeclaration) {
            val subSymbol = subDeclaration.getSymbol() as? KtCallableSymbol ?: return false
            subSymbol.getAllOverriddenSymbols().any { it.psi == superDeclaration }
        }
    }

    override fun isCallableOverrideUsage(reference: PsiReference, declaration: KtNamedDeclaration): Boolean {
        fun KtDeclaration.isTopLevelCallable() = when (this) {
            is KtNamedFunction -> isTopLevel
            is KtProperty -> isTopLevel
            else -> false
        }

        if (declaration.isTopLevelCallable()) return false

        return reference.unwrappedTargets.any { target ->
            when (target) {
                is KtDestructuringDeclarationEntry -> false
                is KtCallableDeclaration -> {
                    if (target.isTopLevelCallable()) return@any false
                    analyze(target) {
                        if (!declaration.canBeAnalysed()) return@any false
                        val targetSymbol = target.getSymbol() as? KtCallableSymbol ?: return@any false
                        declaration.getSymbol() in targetSymbol.getAllOverriddenSymbols()
                    }
                }
                is PsiMethod -> {
                    declaration.toLightMethods().any { superMethod -> MethodSignatureUtil.isSuperMethod(superMethod, target) }
                }
                else -> false
            }
        }
    }

    override fun isUsageInContainingDeclaration(reference: PsiReference, declaration: KtNamedDeclaration): Boolean {
        analyze(declaration) {
            val symbol = declaration.getSymbol()
            val containerSymbol = symbol.getContainingSymbol() ?: return false
            return reference.unwrappedTargets.filterIsInstance(KtDeclaration::class.java).any { candidateDeclaration ->
                val candidateSymbol = candidateDeclaration.getSymbol()
                candidateSymbol != symbol && candidateSymbol.getContainingSymbol() == containerSymbol
            }
        }
    }


    override fun isExtensionOfDeclarationClassUsage(reference: PsiReference, declaration: KtNamedDeclaration): Boolean {
        if (declaration !is KtCallableDeclaration) return false
        analyze(declaration) {
            fun KtClassOrObjectSymbol.isContainerReceiverFor(
                candidateSymbol: KtDeclarationSymbol
            ): Boolean {
                val receiverType = (candidateSymbol as? KtCallableSymbol)?.receiverType ?: return false
                val expandedClassSymbol = receiverType.expandedClassSymbol ?: return false
                return expandedClassSymbol == this || this.isSubClassOf(expandedClassSymbol)
            }

            val symbol = declaration.getSymbol()
            val containerSymbol = symbol.getContainingSymbol() as? KtClassOrObjectSymbol ?: return false
            return reference.unwrappedTargets.filterIsInstance(KtDeclaration::class.java).any { candidateDeclaration ->
                val candidateSymbol = candidateDeclaration.getSymbol()
                candidateSymbol != symbol && containerSymbol.isContainerReceiverFor(candidateSymbol)
            }
        }
    }

    override fun getReceiverTypeSearcherInfo(psiElement: PsiElement, isDestructionDeclarationSearch: Boolean): ReceiverTypeSearcherInfo? {
        return when (psiElement) {
            is KtCallableDeclaration -> {
                analyzeWithReadAction(psiElement) {
                    fun getPsiClassOfKtType(ktType: KtType): PsiClass? {
                        val psi = ktType.asPsiType(psiElement, KtTypeMappingMode.DEFAULT, isAnnotationMethod = false)
                        return (psi as? PsiClassReferenceType)?.resolve()
                    }
                    when (val elementSymbol = psiElement.getSymbol()) {
                        is KtValueParameterSymbol -> {
                            // TODO: The following code handles only constructors. Handle other cases e.g.,
                            //       look for uses of component functions cf [isDestructionDeclarationSearch]
                            val constructorSymbol =
                                elementSymbol.getContainingSymbol() as? KtConstructorSymbol ?: return@analyzeWithReadAction null
                            val containingClassType = getContainingClassType(constructorSymbol) ?: return@analyzeWithReadAction null
                            val psiClass = getPsiClassOfKtType(containingClassType) ?: return@analyzeWithReadAction null

                            ReceiverTypeSearcherInfo(psiClass) {
                                analyze(it) {
                                    val returnType = it.getReturnKtType()
                                    returnType == containingClassType || returnType is KtNonErrorClassType && returnType.ownTypeArguments.any { arg ->
                                        when (arg) {
                                            is KtStarTypeProjection -> false
                                            is KtTypeArgumentWithVariance -> arg.type == containingClassType
                                        }
                                    }
                                }
                            }
                        }
                        is KtCallableSymbol -> {
                            val receiverType =
                                elementSymbol.receiverType
                                    ?: getContainingClassType(elementSymbol)
                                    ?: return@analyzeWithReadAction null
                            val psiClass = getPsiClassOfKtType(receiverType) ?: return@analyzeWithReadAction null

                            ReceiverTypeSearcherInfo(psiClass) {
                                // TODO: stubbed - not exercised by FindUsagesFir Test Suite
                                true
                            }
                        }
                        else ->
                            null
                    }
                }
            }
            is PsiMethod -> {
                val psiClass = psiElement.containingClass ?: return null
                ReceiverTypeSearcherInfo(psiClass) {
                    // TODO: Implement containsTypeOrDerivedInside callback that should determine whether the result type of
                    //       the given KtDeclaration contains (or derived) the type from psiClass (see the discussion in
                    //       CR-410 for details).
                    true
                }
            }
            is PsiMember ->
                null // TODO: stubbed
            else ->
                null // TODO: stubbed? Possibly correct. Update KDoc on the interface to reflect the contract.
        }
    }

    private fun KtAnalysisSession.getContainingClassType(symbol: KtCallableSymbol): KtType? {
        val containingSymbol = symbol.getContainingSymbol() ?: return null
        val classSymbol = containingSymbol as? KtNamedClassOrObjectSymbol ?: return null
        return classSymbol.buildSelfClassType()
    }

    override fun forceResolveReferences(file: KtFile, elements: List<KtElement>) {

    }

    override fun scriptDefinitionExists(file: PsiFile): Boolean {
        return false
    }

    override fun getDefaultImports(file: KtFile): List<ImportPath> {
        return file.getDefaultImports()
    }

    override fun forEachKotlinOverride(
        ktClass: KtClass,
        members: List<KtNamedDeclaration>,
        scope: SearchScope,
        searchDeeply: Boolean,
        processor: (superMember: PsiElement, overridingMember: PsiElement) -> Boolean
    ): Boolean {
        TODO()
    }

    override fun findSuperMethodsNoWrapping(method: PsiElement): List<PsiElement> {
        return emptyList()
    }

    override fun findDeepestSuperMethodsNoWrapping(method: PsiElement): List<PsiElement> {
        return when (val element = method.unwrapped) {
            is PsiMethod -> element.findDeepestSuperMethods().toList()
            is KtCallableDeclaration -> analyze(element) {
                val symbol = element.getSymbol() as? KtCallableSymbol ?: return emptyList()

                val allSuperMethods = symbol.getAllOverriddenSymbols()
                val deepestSuperMethods = allSuperMethods.filter {
                    when (it) {
                        is KtFunctionSymbol -> !it.isOverride
                        is KtPropertySymbol -> !it.isOverride
                        else -> false
                    }
                }

                // FIXME remove .distinct() when getAllOverriddenSymbols stops returning duplicating symbols
                deepestSuperMethods.mapNotNull { it.psi }.distinct()
            }
            else -> emptyList()
        }
    }

    override fun findTypeAliasByShortName(shortName: String, project: Project, scope: GlobalSearchScope): Collection<KtTypeAlias> {
        return KotlinTypeAliasShortNameIndex.get(shortName, project, scope)
    }

    override fun isInProjectSource(element: PsiElement, includeScriptsOutsideSourceRoots: Boolean): Boolean {
        return RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = includeScriptsOutsideSourceRoots).matches(element)
    }

    /**
     * copy-paste in K1: [org.jetbrains.kotlin.idea.core.isOverridable]
     */
    override fun isOverridable(declaration: KtDeclaration): Boolean =
        !declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) &&  // 'private' is incompatible with 'open'
        (declaration.parents.match(KtParameterList::class, KtPrimaryConstructor::class, last = KtClass::class)
                    ?: declaration.parents.match(KtClassBody::class, last = KtClass::class))
                    ?.let { it.isInheritable() || it.isEnum() } == true &&
                isOverridableBySymbol(declaration)

    override fun isInheritable(ktClass: KtClass): Boolean = isOverridableBySymbol(ktClass)

    private fun isOverridableBySymbol(declaration: KtDeclaration) = analyzeWithReadAction(declaration) {
        var declarationSymbol : KtSymbol? = declaration.getSymbol()
        if (declarationSymbol is KtValueParameterSymbol) {
            declarationSymbol = declarationSymbol.generatedPrimaryConstructorProperty
        }
        val symbol = declarationSymbol as? KtSymbolWithModality ?: return@analyzeWithReadAction false
        when (symbol.modality) {
            Modality.OPEN, Modality.SEALED, Modality.ABSTRACT -> true
            Modality.FINAL -> false
        }
    }

    override fun canBeResolvedWithFrontEnd(element: PsiElement): Boolean {
        //TODO FIR: Is the same as PsiElement.hasJavaResolutionFacade() as for FIR?
        return element.originalElement.containingFile != null
    }

    override fun createConstructorHandle(ktDeclaration: KtDeclaration): KotlinSearchUsagesSupport.ConstructorCallHandle {
        return object : KotlinSearchUsagesSupport.ConstructorCallHandle {
            override fun referencedTo(element: KtElement): Boolean {
                val callExpression = element.getNonStrictParentOfType<KtCallElement>() ?: return false
                return withResolvedCall(callExpression) { call ->
                    when (call) {
                        is KtDelegatedConstructorCall -> call.symbol == ktDeclaration.getSymbol()
                        else -> false
                    }
                } ?: false
            }
        }
    }

    override fun createConstructorHandle(psiMethod: PsiMethod): KotlinSearchUsagesSupport.ConstructorCallHandle {
        return object : KotlinSearchUsagesSupport.ConstructorCallHandle {
            override fun referencedTo(element: KtElement): Boolean {
                val callExpression = element.getNonStrictParentOfType<KtCallElement>() ?: return false
                return withResolvedCall(callExpression) {call ->
                    when (call) {
                        is KtDelegatedConstructorCall -> call.symbol.psi == psiMethod
                        else -> false
                    }
                } ?: false
            }
        }
    }
}
