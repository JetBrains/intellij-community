// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import kotlin.math.PI
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.project.structure.*
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.types.Variance

internal class KotlinAnalysisApiBasedDeclarationNavigationPolicyImpl : KotlinDeclarationNavigationPolicy {
    override fun getNavigationElement(declaration: KtDeclaration): KtElement {
        val project = declaration.project
        when (val ktModule = declaration.getKtModule(project)) {
            is KtLibraryModule -> {
                val librarySource = ktModule.librarySources ?: return declaration
                val scope = librarySource.getContentScopeWithCommonDependencies()
                return getCorrespondingDeclarationInLibrarySourceOrBinaryCounterpart(declaration, scope, project) ?: declaration
            }

            else -> return declaration
        }
    }

    override fun getOriginalElement(declaration: KtDeclaration): KtElement {
        val project = declaration.project
        when (val ktModule = declaration.getKtModule(project)) {
            is KtLibrarySourceModule -> {
                val libraryBinary = ktModule.binaryLibrary
                val scope = libraryBinary.getContentScopeWithCommonDependencies()
                return getCorrespondingDeclarationInLibrarySourceOrBinaryCounterpart(declaration, scope, project) ?: declaration
            }

            else -> return declaration
        }
    }

    private fun getCorrespondingDeclarationInLibrarySourceOrBinaryCounterpart(
        declaration: KtDeclaration,
        scope: GlobalSearchScope,
        project: Project
    ): KtElement? {
        return when (declaration) {
            is KtTypeParameter -> {
                val owner = declaration.getOwningTypeParameterOwner() ?: return null
                val correspondingOwner =
                    getCorrespondingDeclarationInLibrarySourceOrBinaryCounterpart(owner, scope, project) as? KtTypeParameterListOwner
                        ?: return null
                correspondingOwner.typeParameters.firstOrNull { it.name == declaration.name }
            }

            is KtClassLikeDeclaration -> getCorrespondingClassLikeDeclaration(declaration, scope, project)
            is KtCallableDeclaration -> getCorrespondingCallableDeclaration(declaration, scope, project)
            else -> null
        }
    }

    private fun getCorrespondingClassLikeDeclaration(
        declaration: KtClassLikeDeclaration,
        scope: GlobalSearchScope,
        project: Project
    ): KtClassLikeDeclaration? {
        val classId = declaration.getClassId() ?: return null
        return KotlinFullClassNameIndex[classId.asFqNameString(), project, scope].firstOrNull()
            ?: KotlinTopLevelTypeAliasFqNameIndex[classId.asFqNameString(), project, scope].firstOrNull()
    }

    private fun getCorrespondingCallableDeclaration(
        declaration: KtCallableDeclaration,
        scope: GlobalSearchScope,
        project: Project
    ): KtCallableDeclaration? {
        val declarationName = declaration.name ?: return null
        when (declaration) {
            is KtParameter -> {
                val owner = declaration.getOwningCallable() ?: return null
                val correspondingOwner = getCorrespondingCallableDeclaration(owner, scope, project) ?: return null
                return correspondingOwner.valueParameters.firstOrNull { it.name == declarationName }
            }
            is KtPrimaryConstructor -> {
                val containingClass = declaration.containingClassOrObject ?: return null
                val correspondingOwner = getCorrespondingClassLikeDeclaration(containingClass, scope, project) as? KtClassOrObject
                    ?: return null
                return correspondingOwner.primaryConstructor
            }
            else -> {
                val candidates = when (val containingClass = declaration.containingClassOrObject) {
                    null -> {
                        val packageFqName = declaration.containingKtFile.packageFqName.takeUnless { it.isRoot }?.asString()
                        val callableName = "${packageFqName?.let { "$it." }.orEmpty()}${declarationName}"
                        when (declaration) {
                            is KtNamedFunction -> KotlinTopLevelFunctionFqnNameIndex[callableName, project, scope]
                            is KtProperty -> KotlinTopLevelPropertyFqnNameIndex[callableName, project, scope]
                            else -> return null
                        }
                    }

                    else -> {
                        val correspondingOwner = getCorrespondingClassLikeDeclaration(containingClass, scope, project) as? KtClassOrObject
                            ?: return null
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
        val a = PI
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
    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun renderTypesForComparasion(declaration: KtCallableDeclaration) = allowAnalysisOnEdt {
        analyze(declaration) {
            buildString {
                val symbol = declaration.getSymbol() as KtCallableSymbol
                symbol.receiverType?.let { receiver ->
                    append(receiver.render(renderer, position = Variance.INVARIANT))
                    append('.')
                }
                if (symbol is KtFunctionLikeSymbol) {
                    symbol.valueParameters.joinTo(this) { it.returnType.render(renderer, position = Variance.INVARIANT) }
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

    private fun KtModule.getContentScopeWithCommonDependencies(): GlobalSearchScope {
        if (platform.isCommon()) return contentScope

        val scopes = buildList {
            add(contentScope)
            allDirectDependencies().filter { it.platform.isCommon() }.mapTo(this) { it.contentScope }
        }
        return GlobalSearchScope.union(scopes)
    }

    companion object {
        private val renderer = KtTypeRendererForSource.WITH_QUALIFIED_NAMES
    }
}