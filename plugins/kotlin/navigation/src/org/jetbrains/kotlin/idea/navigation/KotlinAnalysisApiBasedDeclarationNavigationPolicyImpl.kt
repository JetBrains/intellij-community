// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaGlobalSearchScopeMerger
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.allDirectDependencies
import org.jetbrains.kotlin.analysis.api.renderer.types.KaExpandedTypeRenderingMode
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.types.Variance

@ApiStatus.Internal
open class KotlinAnalysisApiBasedDeclarationNavigationPolicyImpl : KotlinDeclarationNavigationPolicy {
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
        if (common && !bool) {
            if (this?.hasActualModifier() != true) return true
        }
        return bool
    }

    private fun getCorrespondingDeclarationInLibrarySourceOrBinaryCounterpart(
        declaration: KtDeclaration,
        scope: Scope,
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

    private fun getCorrespondingEnumEntry(declaration: KtEnumEntry, scope: Scope, module: KaModule): KtEnumEntry? {
        val enumClass = declaration.containingClassOrObject ?: return null
        val classLikeDeclaration = getCorrespondingClassLikeDeclaration(enumClass, scope, module) as? KtClass ?: return null
        val enumEntryName = declaration.name
        return classLikeDeclaration.declarations.firstOrNull { it is KtEnumEntry && it.name == enumEntryName } as? KtEnumEntry
    }

    private fun getCorrespondingClassLikeDeclaration(
        declaration: KtClassLikeDeclaration,
        scope: Scope,
        module: KaModule
    ): KtClassLikeDeclaration? {
        val classId = declaration.getClassId() ?: return null
        val project = module.project
        val targetPlatform = module.targetPlatform

        val targetDeclaration =
            getClassesByClassId(classId, project, scope).firstOrNull { it.matchesWithPlatform(targetPlatform) } ?:
            getTypeAliasesByClassId(classId, project, scope).firstOrNull { it.matchesWithPlatform(targetPlatform) }
        return targetDeclaration
    }


    private fun getCorrespondingCallableDeclaration(
        declaration: KtCallableDeclaration,
        scope: Scope,
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
                        if (declaration !is KtNamedFunction && declaration !is KtProperty) return null
                        val callableId = CallableId(declaration.containingKtFile.packageFqName, declaration.nameAsName ?: return null)
                        val project = module.project
                        val declarations = getTopLevelCallablesByName(declaration, callableId, project, scope)
                        val targetPlatform = module.targetPlatform
                        declarations.filter { it.matchesWithPlatform(targetPlatform) }
                    }

                    else -> {
                        val correspondingOwner = getCorrespondingClassLikeDeclaration(containingClass, scope, module) as? KtClassOrObject
                            ?: return null
                        val declarations = correspondingOwner.declarations
                        if (declaration is KtProperty) {
                            declarations.firstOrNull { it is KtProperty && it.name == declaration.name }
                                ?.let { return it }

                            if (!declaration.isExtensionDeclaration() && declaration.typeParameters.isEmpty()) {
                                correspondingOwner.primaryConstructor?.valueParameters?.firstOrNull { it.name == declarationName }
                                    ?.let { return it }
                            }
                        }
                        declarations.asSequence()
                    }
                }
                return chooseCallableCandidate(declaration, candidates)
            }
        }
    }

    private fun chooseCallableCandidate(original: KtCallableDeclaration, candidates: Sequence<KtDeclaration>): KtCallableDeclaration? {
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
        candidates: Sequence<KtDeclaration>,
        crossinline matchesByPsi: (C, C) -> Boolean
    ): C? {
        val filteredCandidates = candidates.filterIsInstance<C>().filter { matchesByPsi(original, it) }

        return filteredCandidates.firstOrNull { compareCallableTypesByResolve(original, it) }
            ?: filteredCandidates.firstOrNull()
    }

    private fun KaModule.getContentScopeWithCommonDependencies(): Scope {
        val root = this
        if (targetPlatform.isCommon()) return Scope(listOf(root), contentScope)
        val modules = buildList {
            add(root)
            allDirectDependencies().filterTo(this) { it.targetPlatform.isCommon() }
        }
        return Scope(
            modules,
            KaGlobalSearchScopeMerger.getInstance(project).union(modules.map { it.contentScope })
        )
    }

    protected open fun getTopLevelCallablesByName(
        declaration: KtCallableDeclaration,
        callableId: CallableId,
        project: Project,
        scope: Scope
    ): Sequence<KtCallableDeclaration> = when (declaration) {
        is KtNamedFunction -> KotlinTopLevelFunctionFqnNameIndex[callableId.asSingleFqName().asString(), project, scope.globalSearchScope]
        is KtProperty -> KotlinTopLevelPropertyFqnNameIndex[callableId.asSingleFqName().asString(), project, scope.globalSearchScope]
        else -> error("Unexpected declaration ${declaration::class}")
    }.asSequence()

    protected open fun getTypeAliasesByClassId(classId: ClassId, project: Project, scope: Scope): Sequence<KtTypeAlias> =
        KotlinTopLevelTypeAliasFqNameIndex[classId.asFqNameString(), project, scope.globalSearchScope].asSequence()

    protected open fun getClassesByClassId(classId: ClassId, project: Project, scope: Scope): Sequence<KtClassOrObject> =
        KotlinFullClassNameIndex[classId.asFqNameString(), project, scope.globalSearchScope].asSequence()

    data class Scope(
        val modules: List<KaModule>,
        val globalSearchScope: GlobalSearchScope,
    )

    companion object {
        @KaExperimentalApi
        private val renderer = KaTypeRendererForSource.WITH_QUALIFIED_NAMES.with {
            expandedTypeRenderingMode = KaExpandedTypeRenderingMode.RENDER_EXPANDED_TYPE
        }
    }
}