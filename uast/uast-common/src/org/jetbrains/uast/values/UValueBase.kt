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

abstract class UValueBase : UValue {

    override operator fun plus(other: UValue): UValue =
            if (other is UDependentValue) other + this else UUndeterminedValue

    override operator fun minus(other: UValue): UValue = this + (-other)

    override operator fun times(other: UValue): UValue =
            if (other is UDependentValue) other * this else UUndeterminedValue

    override operator fun div(other: UValue): UValue =
            (other as? UDependentValue)?.inverseDiv(this) ?: UUndeterminedValue

    override operator fun mod(other: UValue): UValue =
            (other as? UDependentValue)?.inverseMod(this) ?: UUndeterminedValue

    override fun unaryMinus(): UValue = UUndeterminedValue

    override fun valueEquals(other: UValue): UValue =
            if (other is UDependentValue || other is UNaNConstant) other.valueEquals(this) else UUndeterminedValue

    override fun valueNotEquals(other: UValue): UValue = !this.valueEquals(other)

    override fun identityEquals(other: UValue): UValue = valueEquals(other)

    override fun identityNotEquals(other: UValue): UValue = !this.identityEquals(other)

    override fun not(): UValue = UUndeterminedValue

    override fun greater(other: UValue): UValue =
            if (other is UDependentValue || other is UNaNConstant) other.less(this) else UUndeterminedValue

    override fun less(other: UValue): UValue = other.greater(this)

    override fun greaterOrEquals(other: UValue) = this.greater(other) or this.valueEquals(other)

    override fun lessOrEquals(other: UValue) = this.less(other) or this.valueEquals(other)

    override fun inc(): UValue = UUndeterminedValue

    override fun dec(): UValue = UUndeterminedValue

    override fun and(other: UValue): UValue =
            if (other is UDependentValue || other == UBooleanConstant.False) other and this else UUndeterminedValue

    override fun or(other: UValue): UValue =
            if (other is UDependentValue || other == UBooleanConstant.True) other or this else UUndeterminedValue

    override fun bitwiseAnd(other: UValue): UValue =
            if (other is UDependentValue) other bitwiseAnd this else UUndeterminedValue

    override fun bitwiseOr(other: UValue): UValue =
            if (other is UDependentValue) other bitwiseOr this else UUndeterminedValue

    override fun bitwiseXor(other: UValue): UValue =
            if (other is UDependentValue) other bitwiseXor this else UUndeterminedValue

    override fun shl(other: UValue): UValue =
            (other as? UDependentValue)?.inverseShiftLeft(this) ?: UUndeterminedValue

    override fun shr(other: UValue): UValue =
            (other as? UDependentValue)?.inverseShiftRight(this) ?: UUndeterminedValue

    override fun ushr(other: UValue): UValue =
            (other as? UDependentValue)?.inverseShiftRightUnsigned(this) ?: UUndeterminedValue

    override fun merge(other: UValue): UValue = when (other) {
        this -> this
        is UVariableValue -> other.merge(this)
        is UCallResultValue -> other.merge(this)
        else -> UPhiValue.create(this, other)
    }

    override val dependencies: Set<UDependency>
        get() = emptySet()

    override fun toConstant(): UConstant? = this as? UConstant

    internal open fun coerceConstant(constant: UConstant): UValue = constant

    override val reachable = true

    override abstract fun toString(): String
}