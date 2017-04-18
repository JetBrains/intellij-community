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

interface UOperand {
    operator fun plus(other: UValue): UValue

    operator fun minus(other: UValue): UValue

    operator fun times(other: UValue): UValue

    operator fun div(other: UValue): UValue

    operator fun mod(other: UValue): UValue

    operator fun unaryMinus(): UValue

    operator fun not(): UValue

    infix fun valueEquals(other: UValue): UValue

    infix fun valueNotEquals(other: UValue): UValue

    infix fun identityEquals(other: UValue): UValue

    infix fun identityNotEquals(other: UValue): UValue

    infix fun greater(other: UValue): UValue

    infix fun less(other: UValue): UValue

    infix fun greaterOrEquals(other: UValue): UValue

    infix fun lessOrEquals(other: UValue): UValue

    fun inc(): UValue

    fun dec(): UValue

    infix fun and(other: UValue): UValue

    infix fun or(other: UValue): UValue

    infix fun bitwiseAnd(other: UValue): UValue

    infix fun bitwiseOr(other: UValue): UValue

    infix fun bitwiseXor(other: UValue): UValue

    infix fun shl(other: UValue): UValue

    infix fun shr(other: UValue): UValue

    infix fun ushr(other: UValue): UValue
}