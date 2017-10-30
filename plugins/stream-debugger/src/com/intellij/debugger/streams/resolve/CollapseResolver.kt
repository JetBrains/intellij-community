// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.resolve

import com.intellij.debugger.streams.trace.TraceElement

/**
 * @author Vitaliy.Bibaev
 */
class CollapseResolver : PartialReductionResolverBase() {
  override fun buildResult(mapping: Map<TraceElement, List<TraceElement>>): ValuesOrderResolver.Result {
    val direct = mutableMapOf<TraceElement, MutableList<TraceElement>>()
    val reverse = mutableMapOf<TraceElement, MutableList<TraceElement>>()

    for (valueAfter in mapping.keys.sortedBy { it.time }) {
      val valuesBefore = mapping[valueAfter]!!.sortedBy { it.time }
      val reverseMapping = mutableListOf<TraceElement>()
      for (valueBefore in valuesBefore) {
        direct[valueBefore] = mutableListOf(valueAfter)
        reverseMapping += valueBefore
      }

      reverse[valueAfter] = reverseMapping
    }

    return ValuesOrderResolver.Result.of(direct, reverse)
  }
}