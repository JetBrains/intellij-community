// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.injection

import com.intellij.patterns.StandardPatterns
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.injection.KotlinFunctionPatternBase
import org.jetbrains.kotlin.idea.base.injection.KotlinReceiverPattern
import org.jetbrains.kotlin.idea.base.injection.KtParameterPatternBase
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.types.Variance

// Methods in this class are used through reflection
@Suppress("unused")
internal object KotlinPatterns : StandardPatterns() {
    @JvmStatic
    fun kotlinParameter() = KtParameterPattern()

    @JvmStatic
    fun kotlinFunction() = KotlinFunctionPattern()

    @JvmStatic
    fun receiver() = KotlinReceiverPattern()
}

context(KtAnalysisSession)
private fun KtType.renderFullyQualifiedName() = render(KtTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.INVARIANT)

// Methods in this class are used through reflection during pattern construction
@Suppress("unused")
internal class KotlinFunctionPattern : KotlinFunctionPatternBase() {
    override fun KtFunction.matchParameters(vararg parameterTypes: String): Boolean {
        analyze(this) {
            val symbol = getSymbol() as? KtFunctionSymbol ?: return false
            val valueParameterSymbols = symbol.valueParameters

            if (valueParameterSymbols.size != parameterTypes.size) return false
            for (i in valueParameterSymbols.indices) {
                val expectedTypeString = parameterTypes[i]
                val valueParamSymbol = valueParameterSymbols[i]

                if (valueParamSymbol.returnType.renderFullyQualifiedName() != expectedTypeString) {
                    return false
                }
            }
        }
        return true
    }

    override fun KtFunction.matchReceiver(receiverFqName: String): Boolean = analyze(this) {
        val symbol = getSymbol() as? KtFunctionSymbol ?: return false
        val receiverType = symbol.receiverType ?: return false
        receiverType.renderFullyQualifiedName() == receiverFqName
    }
}

// Methods in this class are used through reflection during pattern construction
@Suppress("unused")
internal class KtParameterPattern : KtParameterPatternBase() {
    override fun KtParameter.hasAnnotation(fqName: String): Boolean = analyze(this) {
        val paramSymbol = getParameterSymbol()
        paramSymbol is KtValueParameterSymbol && paramSymbol.annotations.any { annotation ->
            annotation.classId?.asFqNameString() == fqName
        }
    }
}