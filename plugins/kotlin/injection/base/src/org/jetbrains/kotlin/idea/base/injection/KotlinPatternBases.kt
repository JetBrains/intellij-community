// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.injection

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PatternConditionPlus
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.PairProcessor
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

// Methods in this class are used through reflection during pattern construction
@ApiStatus.Internal
@Suppress("unused")
abstract class KotlinFunctionPatternBase : PsiElementPattern<KtFunction, KotlinFunctionPatternBase>(KtFunction::class.java) {
    abstract fun KtFunction.matchParameters(vararg parameterTypes: String): Boolean

    abstract fun KtFunction.matchReceiver(receiverFqName: String): Boolean

    fun withParameters(vararg parameterTypes: String): KotlinFunctionPatternBase {
        return withPatternCondition("kotlinFunctionPattern-withParameters") { function, _ ->
            if (function.valueParameters.size != parameterTypes.size) return@withPatternCondition false

            function.matchParameters(*parameterTypes)
        }
    }

    fun withReceiver(receiverFqName: String): KotlinFunctionPatternBase {
        return withPatternCondition("kotlinFunctionPattern-withReceiver") { function, _ ->
            if (function.receiverTypeReference == null) return@withPatternCondition false
            if (receiverFqName == "?") return@withPatternCondition true

            function.matchReceiver(receiverFqName)
        }
    }

    class DefinedInClassCondition(val fqName: String) : PatternCondition<KtFunction>("kotlinFunctionPattern-definedInClass") {
        override fun accepts(element: KtFunction, context: ProcessingContext?): Boolean {
            if (element.parent is KtFile) return false
            return element.containingClassOrObject?.fqName?.asString() == fqName
        }
    }

    fun definedInClass(fqName: String): KotlinFunctionPatternBase = with(DefinedInClassCondition(fqName))

    fun definedInPackage(packageFqName: String): KotlinFunctionPatternBase {
        return withPatternCondition("kotlinFunctionPattern-definedInPackage") { function, _ ->
            if (function.parent !is KtFile) return@withPatternCondition false

            function.containingKtFile.packageFqName.asString() == packageFqName
        }
    }
}

// Methods in this class are used through reflection during pattern construction
@ApiStatus.Internal
@Suppress("unused")
abstract class KtParameterPatternBase : PsiElementPattern<KtParameter, KtParameterPatternBase>(KtParameter::class.java) {
    abstract fun KtParameter.hasAnnotation(fqName: String): Boolean

    fun ofFunction(index: Int, pattern: ElementPattern<Any>): KtParameterPatternBase {
        return with(object : PatternConditionPlus<KtParameter, KtFunction>("KtParameterPattern-ofMethod", pattern) {
            override fun processValues(
                ktParameter: KtParameter,
                context: ProcessingContext,
                processor: PairProcessor<in KtFunction, in ProcessingContext>
            ): Boolean {
                val function = ktParameter.ownerFunction as? KtFunction ?: return true
                return processor.process(function, context)
            }

            override fun accepts(ktParameter: KtParameter, context: ProcessingContext): Boolean {
                val ktFunction = ktParameter.ownerFunction ?: return false

                val parameters = ktFunction.valueParameters
                if (index < 0 || index >= parameters.size || ktParameter != parameters[index]) return false

                return super.accepts(ktParameter, context)
            }
        })
    }

    fun withAnnotation(fqName: String): KtParameterPatternBase {
        return withPatternCondition("KtParameterPattern-withAnnotation") { ktParameter, _ ->
            if (ktParameter.annotationEntries.isEmpty()) return@withPatternCondition false

            ktParameter.hasAnnotation(fqName)
        }
    }
}

@ApiStatus.Internal
@Suppress("unused")
class KotlinReceiverPattern : PsiElementPattern<KtTypeReference, KotlinReceiverPattern>(KtTypeReference::class.java) {
    fun ofFunction(pattern: ElementPattern<Any>): KotlinReceiverPattern {
        return with(object : PatternConditionPlus<KtTypeReference, KtFunction>("KtReceiverPattern-ofMethod", pattern) {
            override fun processValues(
                typeReference: KtTypeReference,
                context: ProcessingContext?,
                processor: PairProcessor<in KtFunction, in ProcessingContext>
            ): Boolean = processor.process(typeReference.parent as? KtFunction, context)

            override fun accepts(typeReference: KtTypeReference, context: ProcessingContext?): Boolean {
                val ktFunction = typeReference.parent as? KtFunction ?: return false
                if (ktFunction.receiverTypeReference != typeReference) return false

                return super.accepts(typeReference, context)
            }
        })
    }
}

private fun <T : PsiElement, Self : PsiElementPattern<T, Self>> PsiElementPattern<T, Self>.withPatternCondition(
    debugName: String, condition: (T, ProcessingContext?) -> Boolean
): Self = with(object : PatternCondition<T>(debugName) {
    override fun accepts(element: T, context: ProcessingContext?): Boolean {
        return condition(element, context)
    }
})