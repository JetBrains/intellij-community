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
import com.intellij.debugger.streams.trace.TraceInfo
import com.intellij.debugger.streams.wrapper.TraceUtil

/**
 * @author Vitaliy.Bibaev
 */
class AppendResolver : ValuesOrderResolver {
  override fun resolve(info: TraceInfo): ValuesOrderResolver.Result {
    val direct = mutableMapOf<TraceElement, MutableList<TraceElement>>()
    val reverse = mutableMapOf<TraceElement, MutableList<TraceElement>>()

    val valuesBefore = TraceUtil.sortedByTime(info.valuesOrderBefore.values)
    val valuesAfter = TraceUtil.sortedByTime(info.valuesOrderAfter.values)

    for ((before, after) in valuesBefore.zip(valuesAfter)) {
      direct[before] = mutableListOf(after)
      reverse[after] = mutableListOf(before)
    }

    return ValuesOrderResolver.Result.of(direct, reverse)
  }
}