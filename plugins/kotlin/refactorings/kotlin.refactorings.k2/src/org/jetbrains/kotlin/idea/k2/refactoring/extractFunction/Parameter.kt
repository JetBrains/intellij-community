// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction

import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.isResolvableInScope
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ControlFlow
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IMutableParameter
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IParameter
import org.jetbrains.kotlin.psi.KtElement

interface Parameter : IParameter<KtType> {
    val originalDescriptor: PsiNamedElement
}

internal sealed class TypePredicate {
    context(KaSession)
    abstract fun isApplicable(ktType: KtType): Boolean
}

internal class SubTypePredicate(private val type: KtType) : TypePredicate() {
    context(KaSession)
    override fun isApplicable(ktType: KtType): Boolean = ktType.isSubTypeOf(type)
}

internal class SuperTypePredicate(private val type: KtType) : TypePredicate() {
    context(KaSession)
    override fun isApplicable(ktType: KtType): Boolean = ktType.isSubTypeOf(type)
}

internal class ExactTypePredicate(private val type: KtType) : TypePredicate() {
    context(KaSession)
    override fun isApplicable(ktType: KtType): Boolean = ktType.isEqualTo(type)
}

internal class AndPredicate(val predicates: Set<TypePredicate>) : TypePredicate() {
    context(KaSession)
    override fun isApplicable(ktType: KtType): Boolean = predicates.all { it.isApplicable(ktType) }
}

internal class MutableParameter(
    override val argumentText: String,
    override val originalDescriptor: PsiNamedElement,
    override val receiverCandidate: Boolean,
    private val originalType: KtType,
    private val scope: KtElement
) : Parameter, IMutableParameter<KtType> {

    private val typePredicates = mutableSetOf<TypePredicate>()

    fun addTypePredicate(typePredicate: TypePredicate) {
        typePredicates.add(typePredicate)
    }

    override var refCount = 0
    var currentName: String? = null
    override val name: String get() = currentName!!

    override var mirrorVarName: String? = null

    context(KaSession)
    private fun allParameterTypeCandidates(): List<KtType> {
        val andPredicate = AndPredicate(typePredicates)
        val typeSet = if (originalType is KaFlexibleType) {
            val lower = originalType.lowerBound
            val upper = originalType.upperBound
            LinkedHashSet<KtType>().apply {
                if (andPredicate.isApplicable(upper)) add(upper)
                if (andPredicate.isApplicable(lower)) add(lower)
            }
        } else linkedSetOf(originalType)

        val addNullableTypes = originalType is KaFlexibleType &&
                originalType.lowerBound.nullability != originalType.upperBound.nullability &&
                typeSet.size > 1
        val superTypes = originalType.getAllSuperTypes().filter {
            andPredicate.isApplicable(it)
        }

        for (superType in superTypes) {
            if (addNullableTypes) {
                typeSet.add(superType.withNullability(KaTypeNullability.NULLABLE))
            }
            typeSet.add(superType)
        }

        return typeSet.toList()
    }

    override fun getParameterTypeCandidates(): List<KtType> {
        analyze(scope) {
            return allParameterTypeCandidates().filter {
                !(it is KaClassType && it.symbol is KaAnonymousObjectSymbol) &&
                        isResolvableInScope(it, scope, mutableSetOf())
            }
        }
    }

    override val parameterType: KtType
        get() = getParameterTypeCandidates().firstOrNull() ?: originalType
}

val ControlFlow<KtType>.possibleReturnTypes: List<KtType>
    get() {
        return listOf(outputValueBoxer.returnType)
    }
