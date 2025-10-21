// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction

import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.allSupertypes
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.components.withNullability
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.isResolvableInScope
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ControlFlow
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IMutableParameter
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.IParameter
import org.jetbrains.kotlin.psi.KtElement

interface Parameter : IParameter<KaType> {
    val originalDescriptor: PsiNamedElement
}

internal sealed class TypePredicate {
    context(_: KaSession)
    abstract fun isApplicable(ktType: KaType): Boolean
}

internal class SubTypePredicate(private val type: KaType) : TypePredicate() {
    context(_: KaSession)
    override fun isApplicable(ktType: KaType): Boolean = ktType.isSubtypeOf(type)
}

internal class SuperTypePredicate(private val type: KaType) : TypePredicate() {
    context(_: KaSession)
    override fun isApplicable(ktType: KaType): Boolean = ktType.isSubtypeOf(type)
}

internal class ExactTypePredicate(private val type: KaType) : TypePredicate() {
    context(_: KaSession)
    override fun isApplicable(ktType: KaType): Boolean = ktType.semanticallyEquals(type)
}

internal class AndPredicate(val predicates: Set<TypePredicate>) : TypePredicate() {
    context(_: KaSession)
    override fun isApplicable(ktType: KaType): Boolean = predicates.all { it.isApplicable(ktType) }
}

internal class MutableParameter(
    override val argumentText: String,
    override val originalDescriptor: PsiNamedElement,
    override val receiverCandidate: Boolean,
    private val originalType: KaType,
    private val scope: KtElement,
    override val contextParameter: Boolean
) : Parameter, IMutableParameter<KaType> {

    private val typePredicates = mutableSetOf<TypePredicate>()

    fun addTypePredicate(typePredicate: TypePredicate) {
        typePredicates.add(typePredicate)
    }

    override var refCount = 0
    var currentName: String? = null
    override val name: String get() = currentName!!

    override var mirrorVarName: String? = null

    context(_: KaSession)
    private fun allParameterTypeCandidates(): List<KaType> {
        val andPredicate = AndPredicate(typePredicates)
        val typeSet = if (originalType is KaFlexibleType) {
            val lower = originalType.lowerBound
            val upper = originalType.upperBound
            LinkedHashSet<KaType>().apply {
                if (andPredicate.isApplicable(upper)) add(upper)
                if (andPredicate.isApplicable(lower)) add(lower)
            }
        } else linkedSetOf(originalType)

        val addNullableTypes = originalType is KaFlexibleType &&
                originalType.lowerBound.nullability != originalType.upperBound.nullability &&
                typeSet.size > 1
        val superTypes = originalType.allSupertypes.filter {
            andPredicate.isApplicable(it)
        }

        for (superType in superTypes) {
            if (addNullableTypes) {
                typeSet.add(superType.withNullability(false))
            }
            typeSet.add(superType)
        }

        return typeSet.toList()
    }

    override fun getParameterTypeCandidates(): List<KaType> {
        analyze(scope) {
            return allParameterTypeCandidates().filter {
                !(it is KaClassType && it.symbol is KaAnonymousObjectSymbol) &&
                        isResolvableInScope(it, scope, mutableSetOf())
            }
        }
    }

    override val parameterType: KaType
        get() = getParameterTypeCandidates().firstOrNull() ?: originalType
}

val ControlFlow<KaType>.possibleReturnTypes: List<KaType>
    get() {
        return listOf(outputValueBoxer.returnType)
    }
