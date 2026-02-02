// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.types.KotlinType

@K1Deprecation
sealed class TypeVariable {
    abstract val classReference: ClassReference
    abstract val typeParameters: List<TypeParameter>
    abstract val owner: TypeVariableOwner
    abstract var state: State
}

@K1Deprecation
sealed class TypeVariableOwner

@K1Deprecation
class FunctionParameter(val owner: KtFunction) : TypeVariableOwner()
@K1Deprecation
class FunctionReturnType(val function: KtFunction) : TypeVariableOwner()
@K1Deprecation
class Property(val property: KtProperty) : TypeVariableOwner()
@K1Deprecation
object TypeArgument : TypeVariableOwner()
@K1Deprecation
object OtherTarget : TypeVariableOwner()


@K1Deprecation
sealed class TypeElementData {
    abstract val typeElement: KtTypeElement
    abstract val type: KotlinType
}

@K1Deprecation
data class TypeElementDataImpl(
    override val typeElement: KtTypeElement,
    override val type: KotlinType
) : TypeElementData()

@K1Deprecation
data class TypeParameterElementData(
    override val typeElement: KtTypeElement,
    override val type: KotlinType,
    val typeParameterDescriptor: TypeParameterDescriptor
) : TypeElementData()

@K1Deprecation
class TypeElementBasedTypeVariable(
    override val classReference: ClassReference,
    override val typeParameters: List<TypeParameter>,
    val typeElement: TypeElementData,
    override val owner: TypeVariableOwner,
    override var state: State
) : TypeVariable()

@K1Deprecation
class TypeBasedTypeVariable(
    override val classReference: ClassReference,
    override val typeParameters: List<TypeParameter>,
    val type: KotlinType,
    override var state: State
) : TypeVariable() {
    override val owner = OtherTarget
}

@K1Deprecation
val TypeVariable.isFixed: Boolean
    get() = state != State.UNKNOWN

@K1Deprecation
fun TypeVariable.setStateIfNotFixed(newState: State) {
    if (!isFixed) {
        state = newState
    }
}

