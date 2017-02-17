/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.uast.values

import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiType
import org.jetbrains.uast.*

interface UConstant : UValue {
    val value: Any?

    val source: UExpression?

    // Used for string concatenation
    fun asString(): String

    // Used for logging / debugging purposes
    override fun toString(): String
}

abstract class UAbstractConstant : UValueBase(), UConstant {
    override fun valueEquals(other: UValue) = when (other) {
        this -> UBooleanConstant.True
        is UConstant -> UBooleanConstant.False
        else -> super.valueEquals(other)
    }

    override fun equals(other: Any?) = other is UAbstractConstant && value == other.value

    override fun hashCode() = value?.hashCode() ?: 0

    override fun toString() = "$value"

    override fun asString() = toString()
}

enum class UNumericType(val prefix: String = "") {
    BYTE("(byte)"),
    SHORT("(short)"),
    INT(),
    LONG("(long)"),
    FLOAT("(float)"),
    DOUBLE();

    fun merge(other: UNumericType): UNumericType {
        if (this == DOUBLE || other == DOUBLE) return DOUBLE
        if (this == FLOAT || other == FLOAT) return FLOAT
        if (this == LONG || other == LONG) return LONG
        return INT
    }
}

abstract class UNumericConstant(val type: UNumericType, override val source: ULiteralExpression?) : UAbstractConstant() {
    override abstract val value: Number

    override fun toString() = "${type.prefix}$value"

    override fun asString() = "$value"
}

private fun PsiType.toNumeric(): UNumericType = when (this) {
    PsiType.LONG -> UNumericType.LONG
    PsiType.INT -> UNumericType.INT
    PsiType.SHORT -> UNumericType.SHORT
    PsiType.BYTE -> UNumericType.BYTE
    PsiType.DOUBLE -> UNumericType.DOUBLE
    PsiType.FLOAT -> UNumericType.FLOAT
    else -> throw AssertionError("Conversion is impossible for type $canonicalText")
}

private fun Int.asType(type: UNumericType): Int = when (type) {
    UNumericType.BYTE -> toByte().toInt()
    UNumericType.SHORT -> toShort().toInt()
    else -> this
}

class UIntConstant(
        rawValue: Int, type: UNumericType = UNumericType.INT, override val source: ULiteralExpression? = null
) : UNumericConstant(type, source) {

    init {
        when (type) {
            UNumericType.INT, UNumericType.SHORT, UNumericType.BYTE -> {}
            else -> throw AssertionError("Incorrect UIntConstant type: $type")
        }
    }

    override val value: Int = rawValue.asType(type)

    constructor(value: Int, type: PsiType): this(value, type.toNumeric())

    override fun plus(other: UValue) = when (other) {
        is UIntConstant -> UIntConstant(value + other.value, type.merge(other.type))
        is ULongConstant -> other + this
        is UFloatConstant -> other + this
        else -> super.plus(other)
    }

    override fun times(other: UValue) = when (other) {
        is UIntConstant -> UIntConstant(value * other.value, type.merge(other.type))
        is ULongConstant -> other * this
        is UFloatConstant -> other * this
        else -> super.times(other)
    }

    override fun div(other: UValue) = when (other) {
        is UIntConstant -> UIntConstant(value / other.value, type.merge(other.type))
        is ULongConstant -> ULongConstant(value / other.value)
        is UFloatConstant -> UFloatConstant.create(value / other.value, type.merge(other.type))
        else -> super.div(other)
    }

    override fun mod(other: UValue) = when (other) {
        is UIntConstant -> UIntConstant(value % other.value, type.merge(other.type))
        is ULongConstant -> ULongConstant(value % other.value)
        is UFloatConstant -> UFloatConstant.create(value % other.value, type.merge(other.type))
        else -> super.mod(other)
    }

    override fun unaryMinus() = UIntConstant(-value, type)

    override fun greater(other: UValue) = when (other) {
        is UIntConstant -> UBooleanConstant.valueOf(value > other.value)
        is ULongConstant -> UBooleanConstant.valueOf(value > other.value)
        is UFloatConstant -> UBooleanConstant.valueOf(value > other.value)
        else -> super.greater(other)
    }

    override fun inc() = UIntConstant(value + 1, type)

    override fun dec() = UIntConstant(value - 1, type)

    override fun bitwiseAnd(other: UValue) = when (other) {
        is UIntConstant -> UIntConstant(value and other.value, type.merge(other.type))
        else -> super.bitwiseAnd(other)
    }

    override fun bitwiseOr(other: UValue) = when (other) {
        is UIntConstant -> UIntConstant(value or other.value, type.merge(other.type))
        else -> super.bitwiseOr(other)
    }

    override fun bitwiseXor(other: UValue) = when (other) {
        is UIntConstant -> UIntConstant(value xor other.value, type.merge(other.type))
        else -> super.bitwiseXor(other)
    }

    override fun shl(other: UValue) = when (other) {
        is UIntConstant -> UIntConstant(value shl other.value, type.merge(other.type))
        else -> super.shl(other)
    }

    override fun shr(other: UValue) = when (other) {
        is UIntConstant -> UIntConstant(value shr other.value, type.merge(other.type))
        else -> super.shr(other)
    }

    override fun ushr(other: UValue) = when (other) {
        is UIntConstant -> UIntConstant(value ushr other.value, type.merge(other.type))
        else -> super.ushr(other)
    }
}

class ULongConstant(override val value: Long, source: ULiteralExpression? = null) : UNumericConstant(UNumericType.LONG, source) {
    override fun plus(other: UValue) = when (other) {
        is ULongConstant -> ULongConstant(value + other.value)
        is UIntConstant -> ULongConstant(value + other.value)
        is UFloatConstant -> other + this
        else -> super.plus(other)
    }

    override fun times(other: UValue) = when (other) {
        is ULongConstant -> ULongConstant(value * other.value)
        is UIntConstant -> ULongConstant(value * other.value)
        is UFloatConstant -> other * this
        else -> super.times(other)
    }

    override fun div(other: UValue) = when (other) {
        is ULongConstant -> ULongConstant(value / other.value)
        is UIntConstant -> ULongConstant(value / other.value)
        is UFloatConstant -> UFloatConstant.create(value / other.value, type.merge(other.type))
        else -> super.div(other)
    }

    override fun mod(other: UValue) = when (other) {
        is ULongConstant -> ULongConstant(value % other.value)
        is UIntConstant -> ULongConstant(value % other.value)
        is UFloatConstant -> UFloatConstant.create(value % other.value, type.merge(other.type))
        else -> super.mod(other)
    }

    override fun unaryMinus() = ULongConstant(-value)

    override fun greater(other: UValue) = when (other) {
        is ULongConstant -> UBooleanConstant.valueOf(value > other.value)
        is UIntConstant -> UBooleanConstant.valueOf(value > other.value)
        is UFloatConstant -> UBooleanConstant.valueOf(value > other.value)
        else -> super.greater(other)
    }

    override fun inc() = ULongConstant(value + 1)

    override fun dec() = ULongConstant(value - 1)

    override fun bitwiseAnd(other: UValue) = when (other) {
        is ULongConstant -> ULongConstant(value and other.value)
        else -> super.bitwiseAnd(other)
    }

    override fun bitwiseOr(other: UValue) = when (other) {
        is ULongConstant -> ULongConstant(value or other.value)
        else -> super.bitwiseOr(other)
    }

    override fun bitwiseXor(other: UValue) = when (other) {
        is ULongConstant -> ULongConstant(value xor other.value)
        else -> super.bitwiseXor(other)
    }

    override fun shl(other: UValue) = when (other) {
        is UIntConstant -> ULongConstant(value shl other.value)
        else -> super.shl(other)
    }

    override fun shr(other: UValue) = when (other) {
        is UIntConstant -> ULongConstant(value shr other.value)
        else -> super.shr(other)
    }

    override fun ushr(other: UValue) = when (other) {
        is UIntConstant -> ULongConstant(value ushr other.value)
        else -> super.ushr(other)
    }
}

open class UFloatConstant protected constructor(
        override val value: Double, type: UNumericType = UNumericType.DOUBLE, source: ULiteralExpression? = null
) : UNumericConstant(type, source) {

    override fun plus(other: UValue) = when (other) {
        is ULongConstant -> create(value + other.value, type.merge(other.type))
        is UIntConstant -> create(value + other.value, type.merge(other.type))
        is UFloatConstant -> create(value + other.value, type.merge(other.type))
        else -> super.plus(other)
    }

    override fun times(other: UValue) = when (other) {
        is ULongConstant -> create(value * other.value, type.merge(other.type))
        is UIntConstant -> create(value * other.value, type.merge(other.type))
        is UFloatConstant -> create(value * other.value, type.merge(other.type))
        else -> super.times(other)
    }

    override fun div(other: UValue) = when (other) {
        is ULongConstant -> create(value / other.value, type.merge(other.type))
        is UIntConstant -> create(value / other.value, type.merge(other.type))
        is UFloatConstant -> create(value / other.value, type.merge(other.type))
        else -> super.div(other)
    }

    override fun mod(other: UValue) = when (other) {
        is ULongConstant -> create(value % other.value, type.merge(other.type))
        is UIntConstant -> create(value % other.value, type.merge(other.type))
        is UFloatConstant -> create(value % other.value, type.merge(other.type))
        else -> super.mod(other)
    }

    override fun greater(other: UValue) = when (other) {
        is ULongConstant -> UBooleanConstant.valueOf(value > other.value)
        is UIntConstant -> UBooleanConstant.valueOf(value > other.value)
        is UFloatConstant -> UBooleanConstant.valueOf(value > other.value)
        else -> super.greater(other)
    }

    override fun unaryMinus() = create(-value, type)

    override fun inc() = create(value + 1, type)

    override fun dec() = create(value - 1, type)

    companion object {
        fun create(value: Double, type: UNumericType = UNumericType.DOUBLE, source: ULiteralExpression? = null) =
                when (type) {
                    UNumericType.DOUBLE, UNumericType.FLOAT -> {
                        if (value.isNaN()) UNaNConstant.valueOf(type)
                        else UFloatConstant(value, type, source)
                    }
                    else -> throw AssertionError("Incorrect UFloatConstant type: $type")
                }

        fun create(value: Double, type: PsiType) = create(value, type.toNumeric())
    }
}

sealed class UNaNConstant(type: UNumericType = UNumericType.DOUBLE) : UFloatConstant(kotlin.Double.NaN, type) {
    object Float : UNaNConstant(UNumericType.FLOAT)

    object Double : UNaNConstant(UNumericType.DOUBLE)

    override fun greater(other: UValue) = UBooleanConstant.False

    override fun less(other: UValue) = UBooleanConstant.False

    override fun greaterOrEquals(other: UValue) = UBooleanConstant.False

    override fun lessOrEquals(other: UValue) = UBooleanConstant.False

    override fun valueEquals(other: UValue) = UBooleanConstant.False

    companion object {
        fun valueOf(type: UNumericType) = when (type) {
            UNumericType.DOUBLE -> Double
            UNumericType.FLOAT -> Float
            else -> throw AssertionError("NaN exists only for Float / Double, but not for $type")
        }
    }
}

class UCharConstant(override val value: Char, override val source: ULiteralExpression? = null) : UAbstractConstant() {
    override fun plus(other: UValue) = when (other) {
        is UIntConstant -> UCharConstant(value + other.value)
        is UCharConstant -> UCharConstant(value + other.value.toInt())
        else -> super.plus(other)
    }

    override fun minus(other: UValue) = when (other) {
        is UIntConstant -> UCharConstant(value - other.value)
        is UCharConstant -> UIntConstant(value - other.value)
        else -> super.plus(other)
    }

    override fun greater(other: UValue) = when (other) {
        is UCharConstant -> UBooleanConstant.valueOf(value > other.value)
        else -> super.greater(other)
    }

    override fun inc() = this + UIntConstant(1)

    override fun dec() = this - UIntConstant(1)

    override fun toString() = "\'$value\'"

    override fun asString() = "$value"
}

sealed class UBooleanConstant(override val value: Boolean) : UAbstractConstant() {
    override val source = null

    object True : UBooleanConstant(true) {
        override fun not() = False

        override fun and(other: UValue) = other as? UBooleanConstant ?: super.and(other)

        override fun or(other: UValue) = True
    }

    object False : UBooleanConstant(false) {
        override fun not() = True

        override fun and(other: UValue) = False

        override fun or(other: UValue) = other as? UBooleanConstant ?: super.or(other)
    }

    companion object {
        fun valueOf(value: Boolean) = if (value) True else False
    }
}

class UStringConstant(override val value: String, override val source: ULiteralExpression? = null) : UAbstractConstant() {

    override fun plus(other: UValue) = when (other) {
        is UConstant -> UStringConstant(value + other.asString())
        else -> super.plus(other)
    }

    override fun greater(other: UValue) = when (other) {
        is UStringConstant -> UBooleanConstant.valueOf(value > other.value)
        else -> super.greater(other)
    }

    override fun asString() = value

    override fun toString() = "\"$value\""
}

class UEnumEntryValueConstant(override val value: PsiEnumConstant, override val source: USimpleNameReferenceExpression? = null) : UAbstractConstant() {
    override fun equals(other: Any?) =
            other is UEnumEntryValueConstant &&
            value.nameIdentifier.text == other.value.nameIdentifier.text &&
            value.containingClass?.qualifiedName == other.value.containingClass?.qualifiedName

    override fun hashCode(): Int {
        var result = 19
        result = result * 13 + value.nameIdentifier.text.hashCode()
        result = result * 13 + (value.containingClass?.qualifiedName?.hashCode() ?: 0)
        return result
    }

    override fun toString() = value.name?.let { "$it (enum entry)" }?: "<unnamed enum entry>"

    override fun asString() = value.name ?: ""
}

class UClassConstant(override val value: PsiType, override val source: UClassLiteralExpression? = null) : UAbstractConstant() {
    override fun toString() = value.name
}

object UNullConstant : UAbstractConstant() {
    override val value = null
    override val source = null
}
