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
import org.jetbrains.uast.values.UVariableValue

abstract class AbstractEvaluationState(override val boundElement: UElement? = null) : UEvaluationState {
    override fun assign(variable: UVariable, value: UValue, at: UElement): AbstractEvaluationState {
        val variableValue = UVariableValue.create(variable, value)
        val prevVariableValue = this[variable]
        return if (prevVariableValue == variableValue) this
        else DelegatingEvaluationState(
                boundElement = at,
                variableValue = variableValue,
                baseState = this
        )
    }

    override fun merge(otherState: UEvaluationState) =
            if (this == otherState) this else MergingEvaluationState(this, otherState)

    override fun equals(other: Any?) =
            other is UEvaluationState && variables == other.variables && variables.all { this[it] == other[it] }

    override fun hashCode(): Int {
        var result = 31
        result = result * 19 + variables.hashCode()
        result = result * 19 + variables.map { this[it].hashCode() }.sum()
        return result
    }

    override fun toString() = variables.joinToString(prefix = "[", postfix = "]", separator = ", ") {
        "${it.psi.name} = ${this[it]}"
    }
}