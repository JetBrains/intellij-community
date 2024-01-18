// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.searching.usages

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.Runnable
import org.jetbrains.kotlin.analysis.api.*
import org.jetbrains.kotlin.analysis.api.calls.KtDelegatedConstructorCall
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.references.unwrappedTargets
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isInheritable
import org.jetbrains.kotlin.idea.search.ReceiverTypeSearcherInfo
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinOverridingCallableSearch
import org.jetbrains.kotlin.idea.searching.inheritors.findAllOverridings
import org.jetbrains.kotlin.idea.searching.kmp.findAllActualForExpect
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.util.match

internal class KotlinK2SearchUsagesSupport : KotlinSearchUsagesSupport {
    override fun isInvokeOfCompanionObject(psiReference: PsiReference, searchTarget: KtNamedDeclaration): Boolean {
        if (searchTarget is KtObjectDeclaration && searchTarget.isCompanion() && psiReference is KtSymbolBasedReference) {
            analyze(psiReference.element) {
                //don't resolve to psi to avoid symbol -> psi, psi -> symbol conversion
                //which doesn't work well for e.g. kotlin.FunctionN classes due to mapping in
                //`org.jetbrains.kotlin.analysis.api.fir.FirDeserializedDeclarationSourceProvider`
                val invokeSymbol = psiReference.resolveToSymbol() ?: return false
                if (invokeSymbol is KtFunctionSymbol && invokeSymbol.name == OperatorNameConventions.INVOKE) {
                    val searchTargetContainerSymbol = searchTarget.getSymbol() as? KtClassOrObjectSymbol ?: return false

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

    override fun getClassNameToSearch(namedElement: PsiNamedElement): String? {
        if (namedElement is KtNamedFunction && OperatorNameConventions.INVOKE.asString() == namedElement.name) {
            val containingClass = namedElement.getParentOfType<KtClassOrObject>(true)
            if (containingClass != null && (containingClass is KtObjectDeclaration || containingClass is KtClass && containingClass.isInterface())) {
                containingClass.name?.let { return it }
            }
        }

        return super.getClassNameToSearch(namedElement)
    }

    override fun actualsForExpected(declaration: KtDeclaration, module: Module?): Set<KtDeclaration> {
        if (declaration is KtParameter) {
            val function = declaration.ownerFunction as? KtCallableDeclaration ?: return emptySet()
            val index = function.valueParameters.indexOf(declaration)
            return actualsForExpected(function, module).mapNotNull { (it as? KtCallableDeclaration)?.valueParameters?.getOrNull(index) }.toSet()
        }
        return declaration.findAllActualForExpect( runReadAction { module?.let { it.moduleTestsWithDependentsScope } ?: declaration.useScope } ).mapNotNull { it.element }.toSet()
    }

    override fun expectedDeclarationIfAny(declaration: KtDeclaration): KtDeclaration? {
        if (declaration.isExpectDeclaration()) return declaration
        if (!declaration.hasActualModifier()) return null
        return analyze(declaration) {
            val symbol: KtDeclarationSymbol = declaration.getSymbol()
            (symbol.getExpectsForActual().mapNotNull { (it.psi as? KtDeclaration) }).firstOrNull()
        }
    }

    override fun isCallableOverride(subDeclaration: KtDeclaration, superDeclaration: PsiNamedElement): Boolean {
        return analyze(subDeclaration) {
            val subSymbol = subDeclaration.getSymbol() as? KtCallableSymbol ?: return false
            subSymbol.getAllOverriddenSymbols().any { it.psi == superDeclaration }
        }
    }

    override fun isCallableOverrideUsage(reference: PsiReference, declaration: KtNamedDeclaration): Boolean {
        if (declaration.isExpectDeclaration() &&
            reference.unwrappedTargets.any { target -> target is KtDeclaration && expectedDeclarationIfAny(target) == declaration }) {
            return true
        }

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
                    if (target === declaration) return@any false
                    analyze(target) {
                        val targetSymbol = target.getSymbol() as? KtCallableSymbol ?: return@any false
                        declaration.originalElement in targetSymbol.getAllOverriddenSymbols().mapNotNull { it.psi?.originalElement }
                    }
                }
                is PsiMethod -> {
                    declaration.toLightMethods().any { superMethod -> MethodSignatureUtil.isSuperMethod(superMethod, target) }
                }
                else -> false
            }
        }
    }

    context(KtAnalysisSession)
    private fun containerSymbol(symbol: KtCallableSymbol): KtDeclarationSymbol? {
        return symbol.getContainingSymbol() ?: symbol.receiverType?.expandedClassSymbol
    }

    override fun isUsageInContainingDeclaration(reference: PsiReference, declaration: KtNamedDeclaration): Boolean {
        val container = analyze(declaration) {
            val symbol = declaration.getSymbol() as? KtCallableSymbol ?: return false
             containerSymbol(symbol)?.psi?.originalElement ?: return false
        }
        return reference.unwrappedTargets.filterIsInstance<KtFunction>().any { candidateDeclaration ->
            if (candidateDeclaration == declaration) return@any false
            if (candidateDeclaration.receiverTypeReference == null && candidateDeclaration.containingFile != container.containingFile) {
                return@any false
            }
            analyze(candidateDeclaration) {
                val candidateSymbol = candidateDeclaration.getSymbol()
                candidateSymbol is KtCallableSymbol && candidateDeclaration != declaration && containerSymbol(candidateSymbol)?.psi == container
            }
        }
    }

    override fun isExtensionOfDeclarationClassUsage(reference: PsiReference, declaration: KtNamedDeclaration): Boolean {
        if (declaration !is KtCallableDeclaration) return false
        val container = analyze(declaration) {
            (declaration.getSymbol().getContainingSymbol() as? KtClassOrObjectSymbol)?.psi?.originalElement as? KtClassOrObject ?: return false
        }

        return reference.unwrappedTargets.filterIsInstance<KtDeclaration>().any { candidateDeclaration ->
            if (candidateDeclaration == declaration) return@any false
            if (candidateDeclaration !is KtCallableDeclaration || candidateDeclaration.receiverTypeReference == null) return@any false
            analyze(candidateDeclaration) {
                if (!container.canBeAnalysed()) return@any false
                val containerSymbol = container.getSymbol() as? KtClassOrObjectSymbol ?: return@any false

                val receiverType = (candidateDeclaration.getSymbol() as? KtCallableSymbol)?.receiverType ?: return@any false
                val expandedClassSymbol = receiverType.expandedClassSymbol ?: return@any false

                expandedClassSymbol == containerSymbol || containerSymbol.isSubClassOf(expandedClassSymbol)
            }
        }
    }

    override fun getReceiverTypeSearcherInfo(psiElement: PsiElement, isDestructionDeclarationSearch: Boolean): ReceiverTypeSearcherInfo? {
        return when (psiElement) {
            is KtCallableDeclaration -> {
                analyze(psiElement) {
                    fun getPsiClassOfKtType(ktType: KtType): PsiClass? {
                        return (ktType as? KtNonErrorClassType)?.classSymbol?.psiSafe<KtClassOrObject>()?.toLightClass()
                    }

                    when (val elementSymbol = psiElement.getSymbol()) {
                        is KtValueParameterSymbol -> {
                            // TODO: The following code handles only constructors. Handle other cases e.g.,
                            //       look for uses of component functions cf [isDestructionDeclarationSearch]
                            val psiClass = PsiTreeUtil.getParentOfType(psiElement, KtClassOrObject::class.java)?.toLightClass() ?: return@analyze null

                            val classPointer = psiClass.createSmartPointer()
                            ReceiverTypeSearcherInfo(psiClass) { declaration ->
                                runReadAction {
                                    analyze(declaration) {
                                        fun KtType.containsClassType(clazz: PsiClass?): Boolean {
                                            if (clazz == null) return false
                                            return this is KtNonErrorClassType && (clazz.unwrapped?.isEquivalentTo(classSymbol.psi) == true || ownTypeArguments.any { arg ->
                                                when (arg) {
                                                    is KtStarTypeProjection -> false
                                                    is KtTypeArgumentWithVariance -> arg.type.containsClassType(clazz)
                                                }
                                            })
                                        }

                                        declaration.getReturnKtType().containsClassType(classPointer.element)
                                    }
                                }
                            }
                        }
                        is KtCallableSymbol -> {
                            val receiverType =
                                elementSymbol.receiverType
                                    ?: getContainingClassType(elementSymbol)
                            val psiClass = receiverType?.let { getPsiClassOfKtType(it) }

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

    context(KtAnalysisSession)
    private fun getContainingClassType(symbol: KtCallableSymbol): KtType? {
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
        for (member in members) {
            if (member is KtCallableDeclaration) {
                val iterator = if (searchDeeply) {
                    member.findAllOverridings().filterIsInstance<KtElement>().iterator()
                } else {
                    DirectKotlinOverridingCallableSearch.search(member).iterator()
                }
                for (psiElement in iterator) {
                    if (!processor(member, psiElement)) return false
                }
            }
        }
        return true
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun findSuperMethodsNoWrapping(method: PsiElement, deepest: Boolean): List<PsiElement> {
        return when (val element = method.unwrapped) {
            is PsiMethod -> (if (deepest) element.findDeepestSuperMethods() else element.findSuperMethods()).toList()
            is KtCallableDeclaration -> allowAnalysisOnEdt {
                analyze(element) {
                    // it's not possible to create symbol for function type parameter, so we need to process this case separately
                    // see KTIJ-25760 and KTIJ-25653
                    if (method is KtParameter && method.isFunctionTypeParameter) return emptyList()

                    val symbol = element.getSymbol() as? KtCallableSymbol ?: return emptyList()

                    val allSuperMethods = if (deepest) symbol.getAllOverriddenSymbols() else symbol.getDirectlyOverriddenSymbols()
                    val deepestSuperMethods = allSuperMethods.filter {
                        when (it) {
                            is KtFunctionSymbol -> !it.isOverride
                            is KtPropertySymbol -> !it.isOverride
                            else -> false
                        }
                    }

                    deepestSuperMethods.mapNotNull { it.psi }
                }
            }
            else -> emptyList()
        }
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

    override fun isInheritable(ktClass: KtClass): Boolean {
        if (ApplicationManager.getApplication().isDispatchThread) {
            return ProgressManager.getInstance().runProcessWithProgressSynchronously(
                Runnable { runReadAction { isOverridableBySymbol(ktClass) } },
                KotlinBundle.message("dialog.title.resolving.inheritable.status"),
                true,
                ktClass.project
            )
        }
        return isOverridableBySymbol(ktClass)
    }

    private fun isOverridableBySymbol(declaration: KtDeclaration) = analyze(declaration) {
        var declarationSymbol : KtSymbol? = declaration.getSymbol()
        if (declarationSymbol is KtValueParameterSymbol) {
            declarationSymbol = declarationSymbol.generatedPrimaryConstructorProperty
        }
        val symbol = declarationSymbol as? KtSymbolWithModality ?: return@analyze false
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
                        is KtDelegatedConstructorCall -> {
                            val constructorSymbol = call.symbol
                            val declarationSymbol = ktDeclaration.getSymbol()
                            constructorSymbol == declarationSymbol || constructorSymbol.getContainingSymbol() == declarationSymbol
                        }
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
