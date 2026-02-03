// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.pullUp

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.components.buildSubstitutor
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaSubstitutor
import org.jetbrains.kotlin.idea.k2.refactoring.pushDown.getSuperTypeEntryBySymbol
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

internal class K2PullUpData(
    val sourceClass: KtClassOrObject,
    val targetClass: PsiNamedElement,
    val membersToMove: Collection<KtNamedDeclaration>,
) {
    fun getTargetClassSymbol(analysisSession: KaSession): KaClassSymbol = with(analysisSession) {
        (targetClass as? PsiClass)?.namedClassSymbol ?: (targetClass as KtClass).symbol as KaClassSymbol
    }

    fun getSuperEntryForTargetClass(analysisSession: KaSession): KtSuperTypeListEntry? = with(analysisSession) {
        getSuperTypeEntryBySymbol(sourceClass, getTargetClassSymbol(analysisSession))
    }

    @OptIn(KaAllowAnalysisOnEdt::class, KaExperimentalApi::class)
    private fun KaSession.collectVisibleTypeParameters(klass: KtClassOrObject): List<KaTypeParameterSymbol> =
        klass.containingKtFile.scopeContext(klass).scopes
            .filter { it.kind is KaScopeKind.TypeParameterScope }
            .flatMap { scopeWithKind ->
                scopeWithKind.scope.classifiers.filterIsInstance<KaTypeParameterSymbol>()
            }

    @OptIn(KaExperimentalApi::class)
    fun getSourceToTargetClassSubstitutor(analysisSession: KaSession): KaSubstitutor = with(analysisSession) {
        buildSubstitutor {
            collectVisibleTypeParameters(sourceClass).forEach { typeParam ->
                val upperBound = typeParam.upperBounds.ifNotEmpty { commonSupertype } ?: builtinTypes.nullableAny
                substitution(typeParam, upperBound)
            }

            val targetClassSymbol = getTargetClassSymbol(analysisSession)

            val inheritanceSubstitutor = createInheritanceTypeSubstitutor(
                subClass = sourceClass.symbol as KaClassSymbol,
                superClass = targetClassSymbol
            ) ?: KaSubstitutor.Empty(token)

            targetClassSymbol.typeParameters.forEach { targetTypeParam ->
                val targetTypeParamType = buildTypeParameterType(targetTypeParam)
                val substituted = inheritanceSubstitutor.substitute(targetTypeParamType)

                if (!substituted.semanticallyEquals(targetTypeParamType)) {
                    sourceClass.symbol.typeParameters.forEach { sourceTypeParam ->
                        val sourceTypeParamType = buildTypeParameterType(sourceTypeParam)
                        if (substituted.semanticallyEquals(sourceTypeParamType)) {
                            substitution(sourceTypeParam, targetTypeParamType)
                        }
                    }
                }
            }
        }
    }

    val isInterfaceTarget: Boolean = (targetClass as? KtClassOrObject)?.let { klass ->
        analyze(targetClass) {
            (klass.symbol as? KaClassSymbol)?.classKind == KaClassKind.INTERFACE
        }
    } == true
}
