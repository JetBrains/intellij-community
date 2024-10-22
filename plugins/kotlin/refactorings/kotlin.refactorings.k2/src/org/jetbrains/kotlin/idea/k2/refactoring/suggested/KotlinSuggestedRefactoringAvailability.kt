// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.suggested

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.suggested.*
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.analysis.api.renderer.types.KaExpandedTypeRenderingMode
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.projectStructure.forcedKaModule
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.idea.refactoring.suggested.KotlinSignatureAdditionalData
import org.jetbrains.kotlin.idea.refactoring.suggested.KotlinSuggestedRefactoringSupportBase
import org.jetbrains.kotlin.idea.refactoring.suggested.defaultValue
import org.jetbrains.kotlin.idea.refactoring.suggested.modifiers
import org.jetbrains.kotlin.idea.refactoring.suggested.receiverType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasBody
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.simpleNameExpressionRecursiveVisitor
import org.jetbrains.kotlin.types.Variance

class KotlinSuggestedRefactoringAvailability(refactoringSupport: SuggestedRefactoringSupport) :
    SuggestedRefactoringAvailability(refactoringSupport) {
    private val HAS_USAGES = Key<Boolean>("KotlinSuggestedRefactoringAvailability.HAS_USAGES")

    override fun amendStateInBackground(state: SuggestedRefactoringState): Iterator<SuggestedRefactoringState> {
        return iterator {
            if (state.additionalData[HAS_USAGES] == null) {
                val declarationCopy = state.restoredDeclarationCopy()
                val useScope = declarationCopy?.useScope
                if (useScope is LocalSearchScope) {
                    val hasUsages = useScope.scope.any { scope ->
                        var hasReferences = false
                        scope.accept(
                            simpleNameExpressionRecursiveVisitor { r ->
                                hasReferences = hasReferences || analyzeCopy(scope as KtElement, KaDanglingFileResolutionMode.PREFER_SELF) {
                                    r.mainReference.resolveToSymbol()?.psi == declarationCopy
                                }
                            })
                        hasReferences
                    }
                    yield(state.withAdditionalData(HAS_USAGES, hasUsages))
                }
            }
        }
    }

    context(KaSession)
    private fun KaType.toTypeInfo(): TypeInfo = TypeInfo(fqText(), this is KaErrorType)

    private data class TypeInfo(val typeFQN: String, val typeError: Boolean)
    private data class SignatureTypes(val returnType: TypeInfo, val parameterTypes: List<TypeInfo>, val receiverType: TypeInfo?)


    context(KaSession)
    private fun signatureTypes(declaration: KtCallableDeclaration): SignatureTypes? {
        val symbol = declaration.symbol as? KaFunctionSymbol
        return if (symbol == null) {
            null
        } else SignatureTypes(
            symbol.returnType.toTypeInfo(),
            symbol.valueParameters.map { it.returnType.toTypeInfo() },
            symbol.receiverType?.toTypeInfo()
        )
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun refineSignaturesWithResolve(state: SuggestedRefactoringState): SuggestedRefactoringState {
        val newDeclaration = state.declaration as? KtCallableDeclaration ?: return state
        val oldDeclaration = state.restoredDeclarationCopy() as? KtCallableDeclaration ?: return state
        oldDeclaration.containingKtFile.forcedKaModule = newDeclaration.getKaModule(newDeclaration.project, useSiteModule = null)

        val descriptorWithOldSignature = allowAnalysisOnEdt { analyzeCopy(oldDeclaration, KaDanglingFileResolutionMode.PREFER_SELF) { signatureTypes(oldDeclaration) } } ?: return state
        val descriptorWithNewSignature = allowAnalysisOnEdt { analyze(newDeclaration) { signatureTypes(newDeclaration) }  } ?: return state

        val oldSignature = state.oldSignature
        val newSignature = state.newSignature

        val (oldReturnType, newReturnType) = refineType(
            oldSignature.type,
            newSignature.type,
            descriptorWithOldSignature.returnType.typeFQN,
            descriptorWithOldSignature.returnType.typeError,
            descriptorWithNewSignature.returnType.typeFQN,
            descriptorWithNewSignature.returnType.typeError
        )

        val improvedOldParameterTypesById = mutableMapOf<Any, String>()
        val improvedNewParameterTypesById = mutableMapOf<Any, String>()
        for (oldParameter in oldSignature.parameters) {
            val id = oldParameter.id
            val newParameter = newSignature.parameterById(id) ?: continue
            val oldIndex = oldSignature.parameterIndex(oldParameter)
            val newIndex = newSignature.parameterIndex(newParameter)
            val (oldType, newType) = refineType(
                oldParameter.type,
                newParameter.type,
                descriptorWithOldSignature.parameterTypes[oldIndex].typeFQN,
                descriptorWithOldSignature.parameterTypes[oldIndex].typeError,
                descriptorWithNewSignature.parameterTypes[newIndex].typeFQN,
                descriptorWithNewSignature.parameterTypes[newIndex].typeError,
            ) // oldType and newType may not be null because arguments of refineType call were all non-null
            improvedOldParameterTypesById[id] = oldType!!
            improvedNewParameterTypesById[id] = newType!!
        }
        val oldParameters = oldSignature.parameters.map { it.copy(type = improvedOldParameterTypesById[it.id] ?: it.type) }
        val newParameters = newSignature.parameters.map { it.copy(type = improvedNewParameterTypesById[it.id] ?: it.type) }

        val oldAdditionalData = oldSignature.additionalData as KotlinSignatureAdditionalData?
        val newAdditionalData = newSignature.additionalData as KotlinSignatureAdditionalData?
        val (oldReceiverType, newReceiverType) = refineType(
            oldAdditionalData?.receiverType,
            newAdditionalData?.receiverType,
            descriptorWithOldSignature.receiverType?.typeFQN,
            descriptorWithOldSignature.receiverType?.typeError,
            descriptorWithNewSignature.receiverType?.typeFQN,
            descriptorWithNewSignature.receiverType?.typeError
        )

        return state.withOldSignature(
                Signature.create(oldSignature.name, oldReturnType, oldParameters, oldAdditionalData?.copy(receiverType = oldReceiverType))!!
            ).withNewSignature(
                Signature.create(newSignature.name, newReturnType, newParameters, newAdditionalData?.copy(receiverType = newReceiverType))!!
            )
    }

    private fun refineType(
        oldTypeInCode: String?,
        newTypeInCode: String?,
        oldTypeResolved: String?,
        oldResolvedTypeError: Boolean?,
        newTypeResolved: String?,
        newResolvedTypeError: Boolean?
    ): Pair<String?, String?> {
        if (oldResolvedTypeError == true || newResolvedTypeError == true) {
            return oldTypeInCode to newTypeInCode
        }

        if (oldTypeInCode != newTypeInCode) {
            if (oldTypeResolved == newTypeResolved) {
                return newTypeInCode to newTypeInCode
            }
        } else {
            if (oldTypeResolved != newTypeResolved) {
                return oldTypeResolved to newTypeResolved
            }
        }

        return oldTypeInCode to newTypeInCode
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun KaType.fqText() = render(position = Variance.INVARIANT)

    override fun detectAvailableRefactoring(state: SuggestedRefactoringState): SuggestedRefactoringData? {
        val declaration = state.declaration
        if (declaration !is KtCallableDeclaration || KotlinSuggestedRefactoringSupportBase.isOnlyRenameSupported(declaration)) {
            if (state.additionalData[HAS_USAGES] == false) return null
            return SuggestedRenameData(declaration as PsiNamedElement, state.oldSignature.name)
        }

        val overridesName = declaration.overridesName()
        if (overridesName == null && state.additionalData[HAS_USAGES] == false) return null

        val oldSignature = state.oldSignature
        val newSignature = state.newSignature
        val updateUsagesData = SuggestedChangeSignatureData.create(state, RefactoringBundle.message("suggested.refactoring.usages"))
        val updateOverridesData = overridesName?.let { updateUsagesData.copy(nameOfStuffToUpdate = it) }

        if (newSignature.parameters.size > oldSignature.parameters.size) {
            val newParametersAtEndWithDefaults = newSignature.parameters.drop(oldSignature.parameters.size)
                .all { oldSignature.parameterById(it.id) == null && it.defaultValue != null } // special case if added new parameters with default values to the end
            // we don't need to update usages if it's the only change
            if (newParametersAtEndWithDefaults) {
                val truncatedNewSignature = Signature.create(
                    newSignature.name,
                    newSignature.type,
                    newSignature.parameters.take(oldSignature.parameters.size),
                    newSignature.additionalData
                )!!
                val refactoringData = detectAvailableRefactoring(
                    oldSignature,
                    truncatedNewSignature,
                    updateUsagesData,
                    updateOverridesData,
                    declaration,
                    declaration.valueParameters.take(oldSignature.parameters.size)
                )

                return when (refactoringData) {
                    is SuggestedChangeSignatureData -> refactoringData
                    is SuggestedRenameData -> updateUsagesData
                    null -> updateOverridesData
                }
            }
        }

        return detectAvailableRefactoring(
            oldSignature, newSignature, updateUsagesData, updateOverridesData, declaration, declaration.valueParameters
        )
    }

    private fun detectAvailableRefactoring(
        oldSignature: Signature,
        newSignature: Signature,
        updateUsagesData: SuggestedChangeSignatureData,
        updateOverridesData: SuggestedChangeSignatureData?,
        declaration: PsiNamedElement,
        parameters: List<KtParameter>
    ): SuggestedRefactoringData? {
        if (hasParameterAddedRemovedOrReordered(oldSignature, newSignature)) return updateUsagesData

        // for non-virtual function we can add or remove receiver for usages but not change its type
        if ((oldSignature.receiverType == null) != (newSignature.receiverType == null)) return updateUsagesData

        val (nameChanges, renameData) = nameChanges(oldSignature, newSignature, declaration, parameters)

        if (hasTypeChanges(oldSignature, newSignature)) {
            return if (nameChanges > 0) updateUsagesData
            else updateOverridesData
        }

        return when {
            renameData != null -> renameData
            nameChanges > 0 -> updateUsagesData
            else -> null
        }
    }

    private fun KtCallableDeclaration.overridesName(): String? {
        return when {
            hasModifier(KtTokens.ABSTRACT_KEYWORD) -> RefactoringBundle.message("suggested.refactoring.implementations")
            hasModifier(KtTokens.OPEN_KEYWORD) -> RefactoringBundle.message("suggested.refactoring.overrides")
            containingClassOrObject?.isInterfaceClass() == true -> if (hasBody()) {
                RefactoringBundle.message("suggested.refactoring.overrides")
            } else {
                RefactoringBundle.message("suggested.refactoring.implementations")
            }

            isExpectDeclaration() -> "actual declarations"
            else -> null
        }
    }

    private fun KtCallableDeclaration.isExpectDeclaration(): Boolean {
        return parentsWithSelf.filterIsInstance<KtDeclaration>().takeWhile { it == this || it is KtClassOrObject }
            .any { it.hasModifier(KtTokens.EXPECT_KEYWORD) }
    }

    override fun hasTypeChanges(oldSignature: Signature, newSignature: Signature): Boolean {
        return super.hasTypeChanges(oldSignature, newSignature) || oldSignature.receiverType != newSignature.receiverType
    }

    override fun hasParameterTypeChanges(oldParam: Parameter, newParam: Parameter): Boolean {
        return super.hasParameterTypeChanges(oldParam, newParam) || oldParam.modifiers != newParam.modifiers
    }
}
