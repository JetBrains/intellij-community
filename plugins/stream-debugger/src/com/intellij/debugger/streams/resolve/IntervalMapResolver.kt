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
package com.intellij.debugger.streams.resolve

import com.intellij.debugger.streams.trace.TraceElement

/**
 * @author Vitaliy.Bibaev
 */
class IntervalMapResolver : PartialReductionResolverBase() {
  override fun buildResult(mapping: Map<TraceElement, List<TraceElement>>): ValuesOrderResolver.Result {
    val direct = mutableMapOf<TraceElement, MutableList<TraceElement>>()
    val reverse = mutableMapOf<TraceElement, MutableList<TraceElement>>()

    for (valueAfter in mapping.keys.sortedBy { it.time }) {
      val valuesBefore = mapping[valueAfter]!!.sortedBy { it.time }
      val reverseMapping = mutableListOf<TraceElement>()
      if (valuesBefore.isNotEmpty()) {
        direct[valuesBefore.first()] = mutableListOf(valueAfter)
        reverseMapping += valuesBefore.first()
        if (valuesBefore.size > 1) {
          direct[valuesBefore.last()] = mutableListOf(valueAfter)
          reverseMapping += valuesBefore.last()
        }
      }

      reverse[valueAfter] = reverseMapping
    }

    return ValuesOrderResolver.Result.of(direct, reverse)
  }
}