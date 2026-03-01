// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

@K1Deprecation
sealed class ClassReference
@K1Deprecation
class DescriptorClassReference(val descriptor: ClassDescriptor) : ClassReference()
@K1Deprecation
class TypeParameterReference(val descriptor: TypeParameterDescriptor) : ClassReference()
@K1Deprecation
object NoClassReference : ClassReference()

@K1Deprecation
val ClassDescriptor.classReference: DescriptorClassReference
    get() = DescriptorClassReference(this)

@K1Deprecation
val ClassReference.descriptor: ClassDescriptor?
    get() = safeAs<DescriptorClassReference>()?.descriptor

@K1Deprecation
class TypeParameter(val boundType: BoundType, val variance: Variance)

@K1Deprecation
sealed class BoundType {
    abstract val label: BoundTypeLabel
    abstract val typeParameters: List<TypeParameter>

    companion object {
        val LITERAL = BoundTypeImpl(LiteralLabel, emptyList())
        val STAR_PROJECTION = BoundTypeImpl(StarProjectionLabel, emptyList())
        val NULL = BoundTypeImpl(NullLiteralLabel, emptyList())
    }
}

@K1Deprecation
class BoundTypeImpl(
    override val label: BoundTypeLabel,
    override val typeParameters: List<TypeParameter>
) : BoundType()


@K1Deprecation
class WithForcedStateBoundType(
    val original: BoundType,
    val forcedState: State
) : BoundType() {
    override val label: BoundTypeLabel
        get() = original.label
    override val typeParameters: List<TypeParameter>
        get() = original.typeParameters
}

@K1Deprecation
fun BoundType.withEnhancementFrom(from: BoundType) = when (from) {
    is BoundTypeImpl -> this
    is WithForcedStateBoundType -> WithForcedStateBoundType(this, from.forcedState)
}

@K1Deprecation
fun BoundType.enhanceWith(state: State?) =
    if (state != null) WithForcedStateBoundType(this, state)
    else this

@K1Deprecation
sealed class BoundTypeLabel

@K1Deprecation
class TypeVariableLabel(val typeVariable: TypeVariable) : BoundTypeLabel()
@K1Deprecation
class TypeParameterLabel(val typeParameter: TypeParameterDescriptor) : BoundTypeLabel()
@K1Deprecation
class GenericLabel(val classReference: ClassReference) : BoundTypeLabel()
@K1Deprecation
object NullLiteralLabel : BoundTypeLabel()
@K1Deprecation
object LiteralLabel : BoundTypeLabel()
@K1Deprecation
object StarProjectionLabel : BoundTypeLabel()


@K1Deprecation
fun TypeVariable.asBoundType(): BoundType =
    BoundTypeImpl(TypeVariableLabel(this), typeParameters)

@K1Deprecation
val BoundType.typeVariable: TypeVariable?
    get() = label.safeAs<TypeVariableLabel>()?.typeVariable

@K1Deprecation
val BoundType.isReferenceToClass: Boolean
    get() = label is TypeVariableLabel || label is GenericLabel