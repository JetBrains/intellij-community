// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.resolve

import com.intellij.debugger.streams.trace.TraceElement
import com.intellij.debugger.streams.trace.TraceInfo
import com.intellij.debugger.streams.wrapper.TraceUtil

/**
 * @author Vitaliy.Bibaev
 */
class PrependResolver : ValuesOrderResolver {
  override fun resolve(info: TraceInfo): ValuesOrderResolver.Result {
    val direct = mutableMapOf<TraceElement, MutableList<TraceElement>>()
    val reverse = mutableMapOf<TraceElement, MutableList<TraceElement>>()

    val valuesBefore = TraceUtil.sortedByTime(info.valuesOrderBefore.values)
    val valuesAfter = TraceUtil.sortedByTime(info.valuesOrderAfter.values)

    if (valuesBefore.isNotEmpty()) {
      val firstBefore = valuesBefore.first()
      val indexOfFirstItemFromSource = valuesAfter.indexOfFirst { it.time > firstBefore.time }
      if (indexOfFirstItemFromSource != -1) {
        for ((before, after) in valuesBefore.zip(valuesAfter.subList(indexOfFirstItemFromSource, valuesAfter.size))) {
          direct[before] = mutableListOf(after)
          reverse[after] = mutableListOf(before)
        }
      }
    }

    return ValuesOrderResolver.Result.of(direct, reverse)
  }
}