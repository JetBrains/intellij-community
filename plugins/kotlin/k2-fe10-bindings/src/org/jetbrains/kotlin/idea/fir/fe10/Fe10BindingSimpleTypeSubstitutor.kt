/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.fir.fe10

import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.getVariance
import org.jetbrains.kotlin.types.model.TypeVariance
import org.jetbrains.kotlin.types.typeUtil.makeNullable

class Fe10BindingSimpleTypeSubstitutor private constructor(
  private val map: Map<KtTypeParameterSymbol, TypeProjection>
) {
    private fun substitute(kotlinType: KotlinType): KotlinType? {
        val unwrappedType = kotlinType.unwrap()
        when (unwrappedType) {
            is SimpleType -> return substitute(unwrappedType)
            is FlexibleType -> {
                val lower = substitute(unwrappedType.lowerBound)?.lowerIfFlexible()
                val upper = substitute(unwrappedType.upperBound)?.upperIfFlexible()
                if (lower == null && upper == null) return null


                return KotlinTypeFactory.flexibleType(
                    lower ?: unwrappedType.lowerBound,
                    upper ?: unwrappedType.upperBound
                )
            }
        }
    }

    private fun substitute(simpleType: SimpleType): UnwrappedType? {
        val replacement = simpleType.constructor.replacement()
        if (replacement != null) {
            assert(replacement.getVariance() == TypeVariance.INV)
            if (simpleType.isMarkedNullable) {
                return replacement.type.makeNullable().unwrap()
            } else {
                return replacement.type.unwrap()
            }
        }

        var hasNotNull = false

        val substitutedArguments = simpleType.arguments.map {
            val newArguments = substituteArgument(it)
            if (newArguments != null) hasNotNull = true
            newArguments ?: it
        }
        if (!hasNotNull) return null

        return KotlinTypeFactory.simpleTypeWithNonTrivialMemberScope(
            simpleType.attributes, simpleType.constructor, substitutedArguments, simpleType.isMarkedNullable,
            simpleType.memberScope
        )
    }

    private fun substituteArgument(argument: TypeProjection): TypeProjection? {
        if (argument.isStarProjection) return null

        val type = argument.type.unwrap()
        val replacement = type.constructor.replacement()
        if (replacement != null && replacement.getVariance() != TypeVariance.INV) {
            if (replacement.isStarProjection) return replacement

            when (type) {
                is SimpleType -> {
                    if (type.isMarkedNullable) {
                        return TypeProjectionImpl(replacement.projectionKind, replacement.type.makeNullable())
                    } else {
                        return replacement
                    }
                }
                // T..T?
                is FlexibleType -> {
                    val newType = FlexibleTypeImpl(replacement.type.lowerIfFlexible(), replacement.type.makeNullable().upperIfFlexible())
                    return TypeProjectionImpl(replacement.projectionKind, newType)
                }
            }

        }

        val newType = substitute(type) ?: return null
        return TypeProjectionImpl(argument.projectionKind, newType)
    }

    private fun TypeConstructor.replacement(): TypeProjection? {
        if (this !is KtSymbolBasedTypeParameterTypeConstructor) return null
        return map[this.ktSBDescriptor.ktSymbol]
    }

    companion object {
        fun substitute(map: Map<KtTypeParameterSymbol, TypeProjection>, type: UnwrappedType) : UnwrappedType {
            if (map.isEmpty()) return type
            return Fe10BindingSimpleTypeSubstitutor(map).substitute(type)?.unwrap() ?: type
        }
    }
}