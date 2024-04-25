// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.types.Variance

interface TypeDescriptor<KotlinType> {
    fun KotlinType.isMeaningful(): Boolean
    fun KotlinType.isError(): Boolean
    val booleanType: KotlinType
    val unitType: KotlinType
    val nothingType: KotlinType
    val nullableAnyType: KotlinType
    fun createListType(argTypes: List<KotlinType>): KotlinType
    fun createTuple(outputValues: List<OutputValue<KotlinType>>): KotlinType
    fun returnType(ktNamedDeclaration: KtNamedDeclaration): KotlinType?
    fun typeArguments(kotlinType: KotlinType): List<KotlinType>

    fun renderType(type: KotlinType, isReceiver: Boolean, variance: Variance): String
    fun renderTypeWithoutApproximation(kotlinType: KotlinType): String

    fun renderForMessage(ktNamedDeclaration: KtNamedDeclaration): String?
    fun renderForMessage(param: IParameter<KotlinType>): String

    fun isResolvableInScope(
        typeToCheck: KotlinType,
        typeParameters: MutableSet<TypeParameter>
    ): Boolean
}