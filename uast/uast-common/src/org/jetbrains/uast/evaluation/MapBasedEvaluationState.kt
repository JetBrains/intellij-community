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
import org.jetbrains.uast.values.UUndeterminedValue
import org.jetbrains.uast.values.UValue
import org.jetbrains.uast.values.UVariableValue

class MapBasedEvaluationState(
  // TODO: Use some immutable map?
  private val map: Map<UVariable, UValue>,
  override val boundElement: UElement? = null
) : UEvaluationState {
  override val variables: Set<UVariable>
    get() = map.keys

  override fun get(variable: UVariable): UValue = map[variable] ?: UUndeterminedValue

  override fun assign(variable: UVariable, value: UValue, at: UElement): UEvaluationState {
    val variableValue = UVariableValue.create(variable, value)
    val prevVariableValue = this[variable]
    return if (prevVariableValue == variableValue) {
      this
    }
    else {
      MapBasedEvaluationState(
        previous = this,
        variable = variable,
        value = variableValue,
        boundElement = at
      )
    }
  }

  override fun merge(otherState: UEvaluationState): MapBasedEvaluationState =
    if (this == otherState) this else MapBasedEvaluationState(this, otherState)

  constructor(boundElement: UElement) : this(mapOf(), boundElement)

  constructor(previous: UEvaluationState, variable: UVariable, value: UValue, boundElement: UElement? = null) :
    this(delegatingMap(previous, variable, value), boundElement)

  constructor(first: UEvaluationState, second: UEvaluationState) :
    this(mergingMap(first, second))

  override fun equals(other: Any?): Boolean = other is MapBasedEvaluationState && map == other.map

  override fun hashCode(): Int = map.hashCode()

  override fun toString(): String = map.toString()

  companion object {
    private fun delegatingMap(previous: UEvaluationState, variable: UVariable, value: UValue): Map<UVariable, UValue> {
      return when (previous) {
        is MapBasedEvaluationState -> previous.map + mapOf(variable to value)
        else -> throw AssertionError("Unknown state implementation")
      }
    }

    private fun mergingMap(first: UEvaluationState, second: UEvaluationState): Map<UVariable, UValue> {
      val allVariables = first.variables + second.variables
      val map = hashMapOf<UVariable, UValue>()
      for (variable in allVariables) {
        map[variable] = first[variable].merge(second[variable])
      }
      return map
    }
  }
}