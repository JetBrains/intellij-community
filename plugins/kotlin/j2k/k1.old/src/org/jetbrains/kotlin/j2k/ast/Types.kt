// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.ast

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.j2k.CodeBuilder
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.Nullability

@K1Deprecation
fun Type.isUnit(): Boolean = this is UnitType

@K1Deprecation
fun Nullability.isNullable(settings: ConverterSettings) = when(this) {
    Nullability.Nullable -> true
    Nullability.NotNull -> false
    Nullability.Default -> !settings.forceNotNullTypes
}

@K1Deprecation
enum class Mutability {
    Mutable,
    NonMutable,
    Default
}

@K1Deprecation
fun Mutability.isMutable() = when(this) {
    Mutability.Mutable -> true
    Mutability.NonMutable -> false
    Mutability.Default -> false //TODO: setting?
}

@K1Deprecation
abstract class MayBeNullableType(nullability: Nullability, val settings: ConverterSettings) : Type() {
    override val isNullable: Boolean = nullability.isNullable(settings)

    protected val isNullableStr: String
        get() = if (isNullable) "?" else ""
}

@K1Deprecation
abstract class NotNullType : Type() {
    override val isNullable: Boolean
        get() = false
}

@K1Deprecation
abstract class Type : Element() {
    abstract val isNullable: Boolean

    open fun toNotNullType(): Type = this

    open fun toNullableType(): Type = this

    override fun equals(other: Any?): Boolean = other is Type && other.canonicalCode() == this.canonicalCode()

    override fun hashCode(): Int = canonicalCode().hashCode()

    override fun toString(): String = canonicalCode()
}

@K1Deprecation
class UnitType : NotNullType() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append("Unit")
    }
}

@K1Deprecation
open class ErrorType : Type() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append("???")
    }

    override val isNullable: Boolean
        get() = false
}

@K1Deprecation
class NullType : ErrorType() {
    override val isNullable: Boolean
        get() = true
}

@K1Deprecation
class ClassType(val referenceElement: ReferenceElement, nullability: Nullability, settings: ConverterSettings)
  : MayBeNullableType(nullability, settings) {

    override fun generateCode(builder: CodeBuilder) {
        builder append referenceElement append isNullableStr
    }

    override fun toNotNullType(): Type = ClassType(referenceElement, Nullability.NotNull, settings).assignPrototypesFrom(this)
    override fun toNullableType(): Type = ClassType(referenceElement, Nullability.Nullable, settings).assignPrototypesFrom(this)

    fun isAnonymous() = referenceElement.name.isEmpty
}

@K1Deprecation
class ArrayType(val elementType: Type, nullability: Nullability, settings: ConverterSettings)
  : MayBeNullableType(nullability, settings) {

    override fun generateCode(builder: CodeBuilder) {
        if (elementType is PrimitiveType) {
            builder append elementType append "Array" append isNullableStr
        }
        else {
            builder append "Array<" append elementType append ">" append isNullableStr
        }
    }

    override fun toNotNullType(): Type = ArrayType(elementType, Nullability.NotNull, settings).assignPrototypesFrom(this)
    override fun toNullableType(): Type = ArrayType(elementType, Nullability.Nullable, settings).assignPrototypesFrom(this)
}

@K1Deprecation
class InProjectionType(val bound: Type) : NotNullType() {
    override fun generateCode(builder: CodeBuilder) {
        builder append "in " append bound
    }
}

@K1Deprecation
class OutProjectionType(val bound: Type) : NotNullType() {
    override fun generateCode(builder: CodeBuilder) {
        builder append "out " append bound
    }
}

@K1Deprecation
class StarProjectionType : NotNullType() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append("*")
    }
}

@K1Deprecation
class PrimitiveType(val name: Identifier) : NotNullType() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append(name)
    }
}

@K1Deprecation
class VarArgType(val type: Type) : NotNullType() {
    override fun generateCode(builder: CodeBuilder) {
        builder.append(type)
    }
}
