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

class UPhiValue private constructor(val values: Set<UValue>): UValueBase() {

    override val dependencies: Set<UDependency> = values.flatMapTo(linkedSetOf()) { it.dependencies }

    override fun equals(other: Any?) = other is UPhiValue && values == other.values

    override fun hashCode() = values.hashCode()

    override fun toString() = values.joinToString(prefix = "Phi(", postfix = ")", separator = ", ")

    companion object {
        private val PHI_LIMIT = 4

        fun create(values: Iterable<UValue>): UValue {
            val flattenedValues = values.flatMapTo(linkedSetOf<UValue>()) { (it as? UPhiValue)?.values ?: listOf(it) }
            if (flattenedValues.size <= 1) {
                throw AssertionError("UPhiValue should contain two or more values: $flattenedValues")
            }
            if (flattenedValues.size > PHI_LIMIT || UUndeterminedValue in flattenedValues) {
                return UUndeterminedValue
            }
            return UPhiValue(flattenedValues)
        }

        fun create(vararg values: UValue) = create(values.asIterable())
    }
}

