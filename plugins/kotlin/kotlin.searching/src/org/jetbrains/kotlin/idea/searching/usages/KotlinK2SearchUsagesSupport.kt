// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.searching.usages

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.defaultType
import org.jetbrains.kotlin.analysis.api.imports.getDefaultImports
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.resolution.KaDelegatedConstructorCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.unwrappedTargets
import org.jetbrains.kotlin.idea.search.ExpectActualSupport
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.expectDeclarationIfAny
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isInheritable
import org.jetbrains.kotlin.idea.search.ReceiverTypeSearcherInfo
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinOverridingCallableSearch
import org.jetbrains.kotlin.idea.searching.inheritors.findAllOverridings
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.util.match

internal class KotlinK2SearchUsagesSupport(private val project: Project) : KotlinSearchUsagesSupport {
    override fun isInvokeOfCompanionObject(psiReference: PsiReference, searchTarget: KtNamedDeclaration): Boolean {
        if (searchTarget is KtObjectDeclaration && searchTarget.isCompanion() && psiReference is KtReference) {
            analyze(psiReference.element) {
                //don't resolve to psi to avoid symbol -> psi, psi -> symbol conversion
                //which doesn't work well for e.g. kotlin.FunctionN classes due to mapping in
                //`org.jetbrains.kotlin.analysis.api.fir.FirDeserializedDeclarationSourceProvider`
                val invokeSymbol = psiReference.resolveToSymbol() ?: return false
                if (invokeSymbol is KaNamedFunctionSymbol && invokeSymbol.name == OperatorNameConventions.INVOKE) {
                    val searchTargetContainerSymbol = searchTarget.symbol

                    fun KaClassSymbol.isInheritorOrSelf(
                        superSymbol: KaClassSymbol?
                    ): Boolean {
                        if (superSymbol == null) return false
                        return superSymbol == this || isSubClassOf(superSymbol)
                    }

                    return searchTargetContainerSymbol.isInheritorOrSelf(invokeSymbol.containingDeclaration as? KaClassSymbol) ||
                            searchTargetContainerSymbol.isInheritorOrSelf(invokeSymbol.receiverParameter?.returnType?.expandedSymbol)
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

    override fun isCallableOverride(subDeclaration: KtDeclaration, superDeclaration: PsiNamedElement): Boolean {
        return analyze(subDeclaration) {
            val subSymbol = subDeclaration.symbol as? KaCallableSymbol ?: return false
            subSymbol.allOverriddenSymbols.any { it.psi == superDeclaration }
        }
    }

    override fun isUsageOfActual(
        reference: PsiReference,
        declaration: KtNamedDeclaration
    ): Boolean = declaration.isExpectDeclaration() &&
            reference.unwrappedTargets.any { target ->
                if (target is KtDeclaration) {
                    val expectedDeclaration = ExpectActualSupport.getInstance(declaration.project)
                        .expectDeclarationIfAny(target)
                    expectedDeclaration == declaration ||
                            //repeat logic of AbstractKtReference.isReferenceTo for calls on companion objects
                            expectedDeclaration is KtObjectDeclaration && expectedDeclaration.isCompanion() && expectedDeclaration.getNonStrictParentOfType<KtClass>() == declaration
                }
                else false
            }

    override fun isCallableOverrideUsage(reference: PsiReference, declaration: KtNamedDeclaration): Boolean {
        val originalDeclaration = declaration.originalElement as? KtDeclaration ?: return false
        fun KtDeclaration.isTopLevelCallable() = when (this) {
            is KtNamedFunction -> isTopLevel
            is KtProperty -> isTopLevel
            else -> false
        }

        if (originalDeclaration.isTopLevelCallable()) return false

        return reference.unwrappedTargets.any { target ->
            when (target) {
                is KtDestructuringDeclarationEntry -> false
                is KtCallableDeclaration -> {
                    if (target.isTopLevelCallable()) return@any false
                    if (target === declaration || target == originalDeclaration) return@any false
                    analyze(target) {
                        val targetSymbol = target.symbol as? KaCallableSymbol ?: return@any false
                        val overriddenDeclarationsInCommon = targetSymbol.allOverriddenSymbols.mapNotNull {
                            val originalElement = it.psi?.originalElement as? KtDeclaration
                            originalElement?.expectDeclarationIfAny() ?: originalElement
                        }
                        //this ignores disabled option `Extracted functions`
                        (originalDeclaration.expectDeclarationIfAny() ?: originalDeclaration) in overriddenDeclarationsInCommon
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
        return reference.unwrappedTargets.filterIsInstance<KtFunction>().any { candidateDeclaration ->
            if (candidateDeclaration == declaration) return@any false
            analyze(candidateDeclaration) {
                val candidateSymbol = candidateDeclaration.symbol as? KaCallableSymbol ?: return@any false

                if (!declaration.canBeAnalysed()) return@any false

                val candidateReceiverType = candidateDeclaration.receiverTypeReference?.type
                val declarationSymbol = declaration.symbol as? KaCallableSymbol ?: return@any false
                val receiverType = declarationSymbol.receiverType

                //do not treat callable with different receivers as overloads
                if (receiverType != null && candidateReceiverType != null && !receiverType.semanticallyEquals(candidateReceiverType)) {
                    return@any false
                }

                val candidateContainer = candidateSymbol.containingDeclaration
                val container = declarationSymbol.containingDeclaration
                if (candidateContainer == null && container == null) { //top level functions should be from the same package
                    declaration.containingKtFile.packageFqName == candidateDeclaration.containingKtFile.packageFqName
                } else if (candidateContainer != null && container != null) { //instance functions should be from the same class/function or same hierarchy
                    candidateContainer == container || container is KaClassSymbol && candidateContainer is KaClassSymbol && container.isSubClassOf(
                        candidateContainer
                    ) && !declarationSymbol.allOverriddenSymbols.contains(candidateSymbol)
                } else false
            }
        }
    }

    override fun isExtensionOfDeclarationClassUsage(reference: PsiReference, declaration: KtNamedDeclaration): Boolean {
        if (declaration !is KtCallableDeclaration) return false
        val container = analyze(declaration) {
            (declaration.symbol.containingDeclaration as? KaClassSymbol)?.psi?.originalElement as? KtClassOrObject ?: return false
        }

        return reference.unwrappedTargets.filterIsInstance<KtDeclaration>().any { candidateDeclaration ->
            if (candidateDeclaration == declaration) return@any false
            if (candidateDeclaration !is KtCallableDeclaration || candidateDeclaration.receiverTypeReference == null) return@any false
            analyze(candidateDeclaration) {
                if (!container.canBeAnalysed()) return@any false
                val containerSymbol = container.symbol as? KaClassSymbol ?: return@any false

                val receiverType = (candidateDeclaration.symbol as? KaCallableSymbol)?.receiverType ?: return@any false
                val expandedClassSymbol = receiverType.expandedSymbol ?: return@any false

                expandedClassSymbol == containerSymbol || containerSymbol.isSubClassOf(expandedClassSymbol)
            }
        }
    }

    override fun getReceiverTypeSearcherInfo(psiElement: PsiElement, isDestructionDeclarationSearch: Boolean): ReceiverTypeSearcherInfo? {
        return when (psiElement) {
            is KtCallableDeclaration -> {
                analyze(psiElement) {
                    fun resolveKtClassOrObject(ktType: KaType): KtClassOrObject? {
                        return (ktType as? KaClassType)?.symbol?.psiSafe<KtClassOrObject>()
                    }

                    when (val elementSymbol = psiElement.symbol) {
                        is KaValueParameterSymbol -> {
                            // TODO: The following code handles only constructors. Handle other cases e.g.,
                            //       look for uses of component functions cf [isDestructionDeclarationSearch]
                            val ktClass = PsiTreeUtil.getParentOfType(psiElement, KtClassOrObject::class.java) ?: return@analyze null

                            val classPointer = ktClass.createSmartPointer()
                            ReceiverTypeSearcherInfo(ktClass) { declaration ->
                                runReadAction {
                                    analyze(declaration) {
                                        fun KaType.containsClassType(clazz: KtClassOrObject?): Boolean {
                                            if (clazz == null) return false
                                            return this is KaClassType && (clazz.isEquivalentTo(symbol.psi) ||
                                                    typeArguments.any { arg ->
                                                        when (arg) {
                                                            is KaStarTypeProjection -> false
                                                            is KaTypeArgumentWithVariance -> arg.type.containsClassType(clazz)
                                                        }
                                                    })
                                        }

                                        declaration.returnType.containsClassType(classPointer.element)
                                    }
                                }
                            }
                        }
                        is KaCallableSymbol -> {
                            val receiverType =
                                elementSymbol.receiverType
                                    ?: getContainingClassType(elementSymbol)
                            val psiClass = receiverType?.let { resolveKtClassOrObject(it) }

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

    context(_: KaSession)
    private fun getContainingClassType(symbol: KaCallableSymbol): KaType? {
        val containingSymbol = symbol.containingDeclaration ?: return null
        val classSymbol = containingSymbol as? KaNamedClassSymbol ?: return null
        return classSymbol.defaultType
    }

    override fun forceResolveReferences(file: KtFile, elements: List<KtElement>) {

    }

    override fun scriptDefinitionExists(file: PsiFile): Boolean {
        return false
    }

    override fun findScriptsWithUsages(declaration: KtNamedDeclaration, processor: (KtFile) -> Boolean): Boolean {
        return true
    }

    override fun getDefaultImports(file: KtFile): List<ImportPath> {
        return KaModuleProvider.getModule(project, file, useSiteModule = null)
            .targetPlatform.getDefaultImports(project)
            .defaultImports
            .map { it.importPath }
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
                    DirectKotlinOverridingCallableSearch.search(member).asIterable().iterator()
                }
                for (psiElement in iterator) {
                    if (!processor(member, psiElement)) return false
                }
            }
        }
        return true
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun findSuperMethodsNoWrapping(method: PsiElement, deepest: Boolean): List<PsiElement> {
        return when (val element = method.unwrapped) {
            is PsiMethod -> (if (deepest) element.findDeepestSuperMethods() else element.findSuperMethods()).toList()
            is KtCallableDeclaration -> allowAnalysisOnEdt {
                analyze(element) {
                    // it's not possible to create symbol for function type parameter, so we need to process this case separately
                    // see KTIJ-25760 and KTIJ-25653
                    if (method is KtParameter && method.isFunctionTypeParameter) return emptyList()

                    val symbol = element.symbol as? KaCallableSymbol ?: return emptyList()

                    val allSuperMethods = if (deepest) symbol.allOverriddenSymbols else symbol.directlyOverriddenSymbols
                    val deepestSuperMethods = allSuperMethods.filter {
                        when (it) {
                            is KaNamedFunctionSymbol -> !it.isOverride
                            is KaPropertySymbol -> !it.isOverride
                            else -> false
                        }
                    }

                    deepestSuperMethods.mapNotNull { it.psi }.toList()
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
            return ActionUtil.underModalProgress(ktClass.project, KotlinBundle.message("dialog.title.resolving.inheritable.status")) {
                runReadAction { isOverridableBySymbol(ktClass) }
            }
        }
        return isOverridableBySymbol(ktClass)
    }

    private fun isOverridableBySymbol(declaration: KtDeclaration) = analyze(declaration) {
        var declarationSymbol: KaSymbol? = declaration.symbol
        if (declarationSymbol is KaValueParameterSymbol) {
            declarationSymbol = declarationSymbol.generatedPrimaryConstructorProperty
        }
        val symbol = declarationSymbol as? KaDeclarationSymbol ?: return@analyze false
        when (symbol.modality) {
            KaSymbolModality.OPEN, KaSymbolModality.SEALED, KaSymbolModality.ABSTRACT -> true
            KaSymbolModality.FINAL -> false
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
                        is KaDelegatedConstructorCall -> {
                            val constructorSymbol = call.symbol
                            val declarationSymbol = ((ktDeclaration.originalElement as? KtDeclaration)?.takeUnless { ktDeclaration.containingFile == element.containingFile } ?: ktDeclaration).symbol
                            constructorSymbol == declarationSymbol || constructorSymbol.containingDeclaration == declarationSymbol
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
                        is KaDelegatedConstructorCall -> call.symbol.psi == psiMethod
                        else -> false
                    }
                } ?: false
            }
        }
    }
}
