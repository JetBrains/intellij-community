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

import org.jetbrains.uast.values.UValue

data class UEvaluationInfo(val value: UValue, val state: UEvaluationState) {
  fun merge(otherInfo: UEvaluationInfo): UEvaluationInfo {
    // info with 'UNothingValue' is just ignored, if other is not UNothingValue
    if (!reachable && otherInfo.reachable) return otherInfo
    if (!otherInfo.reachable && reachable) return this
    // Regular merge
    val mergedValue = value.merge(otherInfo.value)
    val mergedState = state.merge(otherInfo.state)
    return UEvaluationInfo(mergedValue, mergedState)
  }

  fun copy(value: UValue): UEvaluationInfo = if (value != this.value) UEvaluationInfo(value, state) else this

  val reachable: Boolean
    get() = value.reachable
}