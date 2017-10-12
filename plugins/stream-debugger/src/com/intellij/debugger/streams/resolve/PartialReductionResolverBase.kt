// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.resolve

import com.intellij.debugger.streams.trace.TraceElement
import com.intellij.debugger.streams.trace.TraceInfo
import com.intellij.debugger.streams.wrapper.TraceUtil

/**
 * @author Vitaliy.Bibaev
 */
abstract class PartialReductionResolverBase : ValuesOrderResolver {
  override fun resolve(info: TraceInfo): ValuesOrderResolver.Result {
    val valuesBefore = TraceUtil.sortedByTime(info.valuesOrderBefore.values)
    val valuesAfter = TraceUtil.sortedByTime(info.valuesOrderAfter.values)

    val reverseMapping: MutableMap<TraceElement, MutableList<TraceElement>> = mutableMapOf()
    var i = 0
    for (valueAfter in valuesAfter) {
      val reverseList = mutableListOf<TraceElement>()
      while (i + 1 < valuesBefore.size && valuesBefore[i + 1].time < valueAfter.time) {
        reverseList += valuesBefore[i]
        i++
      }

      reverseMapping[valueAfter] = reverseList
    }

    if (valuesAfter.isNotEmpty() && valuesBefore.isNotEmpty()) {
      reverseMapping[valuesAfter.last()]!! += valuesBefore.last()
    }

    return buildResult(reverseMapping)
  }

  abstract fun buildResult(mapping: Map<TraceElement, List<TraceElement>>): ValuesOrderResolver.Result
}