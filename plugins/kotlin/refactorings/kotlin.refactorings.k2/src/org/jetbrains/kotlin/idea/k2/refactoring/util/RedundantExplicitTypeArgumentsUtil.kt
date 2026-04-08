// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.util

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.isAncestor
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.collectDiagnostics
import org.jetbrains.kotlin.analysis.api.components.hasFlexibleNullability
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.projectStructure.copyOrigin
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.KaTypePointer
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeProjection

private val INLINE_REIFIED_FUNCTIONS_WITH_INSIGNIFICANT_TYPE_ARGUMENTS: Set<String> = setOf("kotlin.arrayOf")

fun KaSession.areTypeArgumentsRedundant(
    typeArgumentList: KtTypeArgumentList,
    approximateFlexible: Boolean = false,
): Boolean {
    val callExpression = typeArgumentList.parent as? KtCallExpression ?: return false
    val symbol = callExpression.resolveToCall()?.successfulFunctionCallOrNull()?.symbol ?: return false
    if (isInlineReifiedFunction(symbol)) return false

    if (symbol.receiverType == null && callExpression.valueArguments.isEmpty()) {
        // no reasons to check cases like `val list = emptyList<T>()`
        val parent = callExpression.parent
        val property =
            // `val list = emptyList<T>()`
            parent as? KtProperty
            // `val list = emptyList<T>().smth{...}`
                ?: (parent as? KtDotQualifiedExpression)?.parent as? KtProperty
        if (property != null && property.typeReference == null) {
            return false
        }
    }

    val newCallExpression = buildCallExpressionWithoutTypeArgs(callExpression) ?: return false
    return areTypeArgumentsEqual(callExpression, newCallExpression, approximateFlexible)
}

private fun buildCallExpressionWithoutTypeArgs(element: KtCallExpression): KtCallExpression? {
    val fileCopy = element.containingKtFile.copied()
    val elementCopy = PsiTreeUtil.findSameElementInCopy(element, fileCopy)
    return elementCopy?.also {
        it.typeArgumentList?.delete()
    }
}

private fun KaSession.isInlineReifiedFunction(symbol: KaFunctionSymbol): Boolean =
    symbol is KaNamedFunctionSymbol &&
            symbol.importableFqName?.asString() !in INLINE_REIFIED_FUNCTIONS_WITH_INSIGNIFICANT_TYPE_ARGUMENTS &&
            symbol.isInline &&
            symbol.typeParameters.any { it.isReified }

@OptIn(KaExperimentalApi::class)
context(_: KaSession)
private fun areTypeArgumentsEqual(
    originalCallExpression: KtCallExpression,
    newCallExpression: KtCallExpression,
    approximateFlexible: Boolean,
): Boolean {
    val originalCallInfo = collectCallExpressionInfo(originalCallExpression) ?: return false
    return analyze(newCallExpression) {
        val newCallInfo = collectCallExpressionInfo(newCallExpression) ?: return false

        val originalTypeArgs = restoreTypes(originalCallInfo) ?: return false
        val newTypeArgs = restoreTypes(newCallInfo) ?: return false

        areAllTypesEqual(originalTypeArgs, newTypeArgs, approximateFlexible) &&
                !hasNewDiagnostics(originalCallExpression, newCallExpression)
    }
}

@OptIn(KaExperimentalApi::class)
context(_: KaSession)
private fun collectCallExpressionInfo(callExpression: KtCallExpression): List<KaTypePointer<KaType>>? {
    return callExpression
        .resolveToCall()
        ?.singleFunctionCallOrNull()
        ?.typeArgumentsMapping
        ?.values
        ?.map { it.createPointer() }
}

context(_: KaSession)
private fun areAllTypesEqual(
    originalTypeArgs: List<KaType>,
    newTypeArgs: List<KaType>,
    approximateFlexible: Boolean,
): Boolean {
    return originalTypeArgs.size == newTypeArgs.size &&
            originalTypeArgs.zip(newTypeArgs).all { (originalType, newType) ->
                areTypesEqual(originalType, newType, approximateFlexible)
            }
}

context(_: KaSession)
private fun hasNewDiagnostics(originalCallExpression: KtCallExpression, newCallExpression: KtCallExpression): Boolean {
    val newDiagnostics = newCallExpression.nestedDiagnostics
    if (newDiagnostics.isEmpty()) return false

    val oldDiagnostics = originalCallExpression.nestedDiagnostics

    // Diagnostics cannot be compared directly since they have only identity equals/hashCode
    // Also, original call expression and new call expression files have a different set of psi instances since
    // they effectively in different files
    return newDiagnostics.size != oldDiagnostics.size
}

@OptIn(KaExperimentalApi::class, KaImplementationDetail::class)
context(session: KaSession)
private fun restoreTypes(typePointers: List<KaTypePointer<KaType>>): List<KaType>? =
    typePointers.map { it.restore(session) ?: return null }

// TODO: when KT-63221 is fixed use `diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)` to reduce resolve and avoid psi checks
@OptIn(KaExperimentalApi::class)
context(_: KaSession)
private val KtCallExpression.nestedDiagnostics: List<KaDiagnosticWithPsi<*>>
    get() = containingKtFile
        .collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
        .filter { diagnostic ->
            when (diagnostic) {
                is KaFirDiagnostic.UnresolvedReference,
                is KaFirDiagnostic.BuilderInferenceStubReceiver,
                is KaFirDiagnostic.ImplicitNothingReturnType,
                is KaFirDiagnostic.AmbiguousContextArgument
                    -> isAncestor(diagnostic.psi, strict = false)

                else -> false
            }
        }

context(session: KaSession)
private fun areTypesEqual(
    type1: KaType,
    type2: KaType,
    approximateFlexible: Boolean,
): Boolean = when (type1) {
    is KaTypeParameterType if type2 is KaTypeParameterType -> {
        type1.name == type2.name
    }

    is KaDefinitelyNotNullType if type2 is KaDefinitelyNotNullType -> {
        areTypesEqual(type1.original, type2.original, approximateFlexible)
    }

    else -> (approximateFlexible || type1.hasFlexibleNullability == type2.hasFlexibleNullability) &&
            type1.semanticallyEquals(type2)
}

context(_: KaSession)
fun KtTypeProjection.canBeReplacedWithUnderscore(callExpression: KtCallExpression): Boolean {
    val newCallExpression = buildCallExpressionWithUnderscores(callExpression, this)
        ?: return false

    return areTypeArgumentsEqual(callExpression, newCallExpression, false)
}

@OptIn(KaExperimentalApi::class)
private fun buildCallExpressionWithUnderscores(element: KtCallExpression, typeProjectionToReplace: KtTypeProjection): KtCallExpression? {
    val fileCopy = if (element.containingKtFile.copyOrigin != null) {
        /**
         * In intention actions such as
         * [org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction],
         * the PSI context is recomputed on a copied file right before the intention is invoked.
         * In such cases, we can reuse and modify that existing copy instead of creating a new one.
         *
         * @see org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction.perform
         */
        element.containingFile
    } else {
        element.containingKtFile.copied()
    }
    val elementCopy = PsiTreeUtil.findSameElementInCopy(element, fileCopy)
    val typeProjectionCopy = PsiTreeUtil.findSameElementInCopy(typeProjectionToReplace, fileCopy)
    return elementCopy?.also {
        val newTypeProjection = KtPsiFactory(element.project).createTypeArgument("_")
        typeProjectionCopy.replace(newTypeProjection)
    }
}
