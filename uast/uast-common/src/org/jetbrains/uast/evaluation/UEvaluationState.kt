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
package org.jetbrains.uast.evaluation

import org.jetbrains.uast.UElement
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.values.UValue

// Role: stores current values for all variables (and may be something else)
// Immutable
interface UEvaluationState {
    val boundElement: UElement?

    val variables: Set<UVariable>

    operator fun get(variable: UVariable): UValue

    // Creates new evaluation state with state[variable] = value and boundElement = at
    fun assign(variable: UVariable, value: UValue, at: UElement): UEvaluationState

    // Merged two states
    fun merge(otherState: UEvaluationState): UEvaluationState
}

fun UElement.createEmptyState(): UEvaluationState = MapBasedEvaluationState(this)