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

import com.intellij.psi.PsiType
import org.jetbrains.uast.UVariable

class UVariableValue private constructor(
  val variable: UVariable,
  value: UValue,
  dependencies: Set<UDependency>
) : UDependentValue(value, dependencies), UDependency {

  override fun identityEquals(other: UValue): UValue =
    if (this == other) super.valueEquals(other)
    else when (variable.psi.type) {
      PsiType.BYTE, PsiType.FLOAT, PsiType.DOUBLE, PsiType.LONG,
      PsiType.SHORT, PsiType.INT, PsiType.CHAR, PsiType.BOOLEAN -> super.valueEquals(other)

      else -> UUndeterminedValue
    }

  override fun merge(other: UValue): UValue = when (other) {
    this -> this
    value -> this
    is UDependentValue -> {
      val allDependencies = dependencies + other.dependencies
      when {
        other !is UVariableValue || variable != other.variable -> UPhiValue.create(this, other)
        value != other.value -> create(variable, value.merge(other.value), allDependencies)
        else -> create(variable, value, allDependencies)
      }
    }
    else -> UPhiValue.create(this, other)
  }

  override fun copy(dependencies: Set<UDependency>) =
    if (dependencies == this.dependencies) this else create(variable, value, dependencies)

  override fun coerceConstant(constant: UConstant): UValue =
    if (constant == toConstant()) this
    else create(variable, value.coerceConstant(constant), dependencies)

  override fun equals(other: Any?): Boolean =
    other is UVariableValue
    && variable == other.variable
    && value == other.value
    && dependencies == other.dependencies

  override fun hashCode(): Int {
    var result = 31
    result = result * 19 + variable.hashCode()
    result = result * 19 + value.hashCode()
    result = result * 19 + dependencies.hashCode()
    return result
  }

  override fun toString(): String = "(var ${variable.name ?: "<unnamed>"} = ${super.toString()})"

  companion object {

    private fun Set<UDependency>.filterNot(variable: UVariable) =
      filterTo(linkedSetOf()) { it !is UVariableValue || variable != it.variable }

    fun create(variable: UVariable, value: UValue, dependencies: Set<UDependency> = emptySet()): UVariableValue {
      when (variable.psi.type) {
        PsiType.BYTE, PsiType.SHORT -> {
          val constant = value.toConstant()
          if (constant is UIntConstant && constant.type == UNumericType.INT) {
            val castConstant = UIntConstant(constant.value, variable.psi.type)
            return create(variable, value.coerceConstant(castConstant), dependencies)
          }
        }
      }
      val dependenciesWithoutSelf = dependencies.filterNot(variable)
      return when {
        value is UVariableValue
        && variable == value.variable
        && dependenciesWithoutSelf == value.dependencies -> value

        value is UDependentValue -> {
          val valueDependencies = value.dependencies.filterNot(variable)
          val modifiedValue = value.copy(valueDependencies)
          UVariableValue(variable, modifiedValue, dependenciesWithoutSelf)
        }

        else -> UVariableValue(variable, value, dependenciesWithoutSelf)
      }
    }
  }
}
