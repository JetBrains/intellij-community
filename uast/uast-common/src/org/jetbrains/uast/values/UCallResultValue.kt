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

import org.jetbrains.uast.UElement
import org.jetbrains.uast.UResolvable

// Value of something resolvable (e.g. call or property access)
// that we cannot or do not want to evaluate
class UCallResultValue(val resolvable: UResolvable, val arguments: List<UValue>) : UValueBase(), UDependency {

    private val argumentsHashCode = arguments.hashCode()

    override fun merge(other: UValue): UValue = when (other) {
        this -> this
        is UCallResultValue -> {
            if (resolvable == other.resolvable) {
                UCallResultValue(resolvable, arguments.map { UUndeterminedValue })
            }
            else {
                UPhiValue.create(this, other)
            }
        }
        else -> UPhiValue.create(this, other)
    }

    override fun equals(other: Any?) =
        other is UCallResultValue && resolvable == other.resolvable &&
        argumentsHashCode == other.argumentsHashCode && arguments == other.arguments

    override fun hashCode() = resolvable.hashCode() * 19 + argumentsHashCode

    override fun toString(): String {
        return "external ${(resolvable as? UElement)?.asRenderString() ?: "???"}(${arguments.joinToString()})"
    }
}
