// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.navigation

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinGlobalSearchScopeMerger
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.allDirectDependencies
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.types.Variance

internal class KotlinAnalysisApiBasedDeclarationNavigationPolicyImpl : KotlinDeclarationNavigationPolicy {
    override fun getNavigationElement(declaration: KtDeclaration): KtElement {
        val ktFile = declaration.containingKtFile
        if (!ktFile.isCompiled) return declaration
        val project = ktFile.project
        return when (val module = ktFile.getKaModule(project, useSiteModule = null) ) {
            is KaLibraryModule -> getCorrespondingDeclarationInLibrarySourceOrBinaryCounterpart(
                module.librarySources ?: return declaration,
                declaration,
                module
            )

            else -> declaration
        }
    }

    override fun getOriginalElement(declaration: KtDeclaration): KtElement {
        val ktFile = declaration.containingKtFile
        if (ktFile.isCompiled) return declaration
        val project = ktFile.project
        return when (val module = ktFile.getKaModule(project, useSiteModule = null)) {
            is KaLibrarySourceModule -> getCorrespondingDeclarationInLibrarySourceOrBinaryCounterpart(
                module.binaryLibrary,
                declaration,
                module
            )

            else -> declaration
        }
    }

    private fun getCorrespondingDeclarationInLibrarySourceOrBinaryCounterpart(
        library: KaModule,
        declaration: KtDeclaration,
        module: KaModule
    ): KtElement {
        val scope = library.getContentScopeWithCommonDependencies()
        return getCorrespondingDeclarationInLibrarySourceOrBinaryCounterpart(declaration, scope, module) ?: declaration
    }

    private fun KtDeclaration?.matchesWithPlatform(targetPlatform: TargetPlatform): Boolean {
        val common = targetPlatform.isCommon()
        val bool = this?.isExpectDeclaration() == common
        return bool
    }

    private fun getCorrespondingDeclarationInLibrarySourceOrBinaryCounterpart(
        declaration: KtDeclaration,
        scope: GlobalSearchScope,
        module: KaModule
    ): KtElement? {
        return when (declaration) {
            is KtTypeParameter -> {
                val owner = declaration.getOwningTypeParameterOwner() ?: return null
                val correspondingOwner =
                    getCorrespondingDeclarationInLibrarySourceOrBinaryCounterpart(owner, scope, module) as? KtTypeParameterListOwner
                        ?: return null
                correspondingOwner.typeParameters.firstOrNull { it.name == declaration.name }
            }

            is KtEnumEntry -> getCorrespondingEnumEntry(declaration, scope, module)
            is KtClassLikeDeclaration -> getCorrespondingClassLikeDeclaration(declaration, scope, module)
            is KtCallableDeclaration -> getCorrespondingCallableDeclaration(declaration, scope, module)
            else -> null
        }
    }

    private fun getCorrespondingEnumEntry(declaration: KtEnumEntry, scope: GlobalSearchScope, module: KaModule): KtEnumEntry? {
        val enumClass = declaration.containingClassOrObject ?: return null
        val classLikeDeclaration = getCorrespondingClassLikeDeclaration(enumClass, scope, module) as? KtClass ?: return null
        val enumEntryName = declaration.name
        return classLikeDeclaration.declarations.firstOrNull { it is KtEnumEntry && it.name == enumEntryName } as? KtEnumEntry
    }

    private fun getCorrespondingClassLikeDeclaration(
        declaration: KtClassLikeDeclaration,
        scope: GlobalSearchScope,
        module: KaModule
    ): KtClassLikeDeclaration? {
        val classId = declaration.getClassId() ?: return null
        val project = module.project
        val targetPlatform = module.targetPlatform
        val declarations =
            KotlinFullClassNameIndex[classId.asFqNameString(), project, scope].firstOrNull { it.matchesWithPlatform(targetPlatform) } ?:
            KotlinTopLevelTypeAliasFqNameIndex[classId.asFqNameString(), project, scope].firstOrNull { it.matchesWithPlatform(targetPlatform) }
        return declarations
    }

    private fun getCorrespondingCallableDeclaration(
        declaration: KtCallableDeclaration,
        scope: GlobalSearchScope,
        module: KaModule
    ): KtElement? {
        val declarationName = declaration.name ?: return null
        when (declaration) {
            is KtParameter -> {
                val owner = declaration.getOwningCallable() ?: return null
                val correspondingOwner = getCorrespondingCallableDeclaration(owner, scope, module) as? KtCallableDeclaration ?: return null
                return correspondingOwner.valueParameters.firstOrNull { it.name == declarationName }
            }
            is KtPrimaryConstructor -> {
                val containingClass = declaration.containingClassOrObject ?: return null
                val correspondingOwner = getCorrespondingClassLikeDeclaration(containingClass, scope, module) as? KtClassOrObject
                    ?: return null
                return correspondingOwner.primaryConstructor ?: correspondingOwner
            }
            else -> {
                val candidates = when (val containingClass = declaration.containingClassOrObject) {
                    null -> {
                        val packageFqName = declaration.containingKtFile.packageFqName.takeUnless { it.isRoot }?.asString()
                        val callableName = "${packageFqName?.let { "$it." }.orEmpty()}${declarationName}"
                        val project = module.project
                        when (declaration) {
                            is KtNamedFunction -> KotlinTopLevelFunctionFqnNameIndex[callableName, project, scope]
                            is KtProperty -> KotlinTopLevelPropertyFqnNameIndex[callableName, project, scope]
                            else -> return null
                        }
                    }

                    else -> {
                        val correspondingOwner = getCorrespondingClassLikeDeclaration(containingClass, scope, module) as? KtClassOrObject
                            ?: return null
                        if (declaration is KtProperty && correspondingOwner.isData() && !declaration.isExtensionDeclaration() && declaration.typeParameters.isEmpty()) {
                            correspondingOwner.primaryConstructor?.valueParameters?.firstOrNull { it.name == declarationName }?.let { return it }
                        }
                        correspondingOwner.declarations
                    }
                }
                return chooseCallableCandidate(declaration, candidates)
            }
        }
    }

    private fun chooseCallableCandidate(original: KtCallableDeclaration, candidates: Collection<KtDeclaration>): KtCallableDeclaration? {
        return when (original) {
            is KtConstructor<*> -> chooseCallableCandidate(original, candidates) { original, candidate ->
                constructorsMatchesByPsi(original, candidate)
            }
            is KtNamedFunction -> chooseCallableCandidate(original, candidates) { original, candidate ->
                functionsMatchesByPsi(original, candidate)
            }
            is KtProperty -> chooseCallableCandidate(original, candidates) { original, candidate ->
                propertiesMatchesByPsi(original, candidate)
            }
            else -> null
        }
    }

    private fun propertiesMatchesByPsi(firstProperty: KtProperty, secondProperty: KtProperty): Boolean {
        if (firstProperty.name != secondProperty.name) return false
        if (firstProperty.isExtensionDeclaration() != secondProperty.isExtensionDeclaration()) return false

        if (!typeParameterMatches(firstProperty, secondProperty)) return false

        return true
    }

    private fun functionsMatchesByPsi(first: KtNamedFunction, second: KtNamedFunction): Boolean {
        if (first.name != second.name) return false
        if (first.isExtensionDeclaration() != second.isExtensionDeclaration()) return false

        if (!valueParameterMatches(first, second)) return false
        if (!typeParameterMatches(first, second)) return false

        return true
    }


    private fun constructorsMatchesByPsi(first: KtConstructor<*>, second: KtConstructor<*>): Boolean {
        if (first is KtPrimaryConstructor != second is KtPrimaryConstructor) return false
        if (!valueParameterMatches(first, second)) return false

        return true
    }

    private fun valueParameterMatches(firstValueParamOwner: KtCallableDeclaration, secondValueParameOwner: KtCallableDeclaration): Boolean {
        val firstValueParameters = firstValueParamOwner.valueParameters
        val secondValueParameters = secondValueParameOwner.valueParameters

        if (firstValueParameters.size != secondValueParameters.size) return false
        for (i in firstValueParameters.indices) {
            val first = firstValueParameters[i]
            val second = secondValueParameters[i]
            if (first.name != second.name) return false
            if (first.isVarArg != second.isVarArg) return false
        }
        return true
    }

    private fun typeParameterMatches(
        firstTypeParamOwner: KtTypeParameterListOwner,
        secondTypeParamOwner: KtTypeParameterListOwner
    ): Boolean {
        val firstTypeParameters = firstTypeParamOwner.typeParameters
        val secondTypeParameters = secondTypeParamOwner.typeParameters
        if (firstTypeParameters.size != secondTypeParameters.size) return false
        for (i in firstTypeParameters.indices) {
            val first = firstTypeParameters[i]
            val second = secondTypeParameters[i]
            if (first.name != second.name) return false
        }
        return true
    }

    private fun KtParameter.getOwningCallable(): KtCallableDeclaration? {
        val parameterList = parent as? KtParameterList ?: return null
        return parameterList.parent as? KtCallableDeclaration
    }

    private fun KtTypeParameter.getOwningTypeParameterOwner(): KtTypeParameterListOwner? {
        val parameterList = parent as? KtTypeParameterList ?: return null
        return parameterList.parent as? KtTypeParameterListOwner
    }

    private fun compareCallableTypesByResolve(first: KtCallableDeclaration, second: KtCallableDeclaration): Boolean {
        // symbols should be rendered from corresponding sessions
        val firstRendered = renderTypesForComparasion(first)
        val secondRendered = renderTypesForComparasion(second)
        return firstRendered == secondRendered
    }

    // Maybe called from EDT by IJ Platfrom :(
    @OptIn(KaAllowAnalysisOnEdt::class, KaExperimentalApi::class)
    private fun renderTypesForComparasion(declaration: KtCallableDeclaration) = allowAnalysisOnEdt {
        @OptIn(KaAllowAnalysisFromWriteAction::class)
        allowAnalysisFromWriteAction {
            analyze(declaration) {
                buildString {
                    val symbol = declaration.symbol as KaCallableSymbol
                    symbol.receiverType?.let { receiverType ->
                        append(receiverType.render(renderer, position = Variance.INVARIANT))
                        append('.')
                    }
                    if (symbol is KaFunctionSymbol) {
                        symbol.valueParameters.joinTo(this) { it.returnType.render(renderer, position = Variance.INVARIANT) }
                    }
                }
            }
        }
    }

    private inline fun <reified C : KtCallableDeclaration> chooseCallableCandidate(
        original: C,
        candidates: Collection<KtDeclaration>,
        matchesByPsi: (C, C) -> Boolean
    ): C? {
        val filteredCandidates = candidates.filterIsInstance<C>().filter { matchesByPsi(original, it) }

        return when (filteredCandidates.size) {
            0 -> null
            1 -> filteredCandidates.single()
            else -> filteredCandidates.firstOrNull { compareCallableTypesByResolve(original, it) }
        }
    }

    private fun KaModule.getContentScopeWithCommonDependencies(): GlobalSearchScope {
        if (targetPlatform.isCommon()) return contentScope

        val scopes = buildList {
            add(contentScope)
            allDirectDependencies().filter { it.targetPlatform.isCommon() }.mapTo(this) { it.contentScope }
        }
        return KotlinGlobalSearchScopeMerger.getInstance(project).union(scopes)
    }

    companion object {
        @KaExperimentalApi
        private val renderer = KaTypeRendererForSource.WITH_QUALIFIED_NAMES
    }
}