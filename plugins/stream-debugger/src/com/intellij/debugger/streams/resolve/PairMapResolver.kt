// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.resolve

import com.intellij.debugger.streams.resolve.ValuesOrderResolver.Result.of
import com.intellij.debugger.streams.trace.TraceElement
import com.intellij.debugger.streams.trace.TraceInfo
import com.intellij.debugger.streams.wrapper.TraceUtil

/**
 * @author Vitaliy.Bibaev
 */
class PairMapResolver : ValuesOrderResolver {
  override fun resolve(info: TraceInfo): ValuesOrderResolver.Result {
    val direct = mutableMapOf<TraceElement, MutableList<TraceElement>>()
    val reverse = mutableMapOf<TraceElement, MutableList<TraceElement>>()

    val valuesBefore = TraceUtil.sortedByTime(info.valuesOrderBefore.values)
    val valuesAfter = TraceUtil.sortedByTime(info.valuesOrderAfter.values)

    val beforeIterator = valuesBefore.iterator()
    val afterIterator = valuesAfter.iterator()

    var after: TraceElement? = null
    while (beforeIterator.hasNext()) {
      val before = beforeIterator.next()
      if (after != null) {
        direct.add(before, after)
        reverse.add(after, before)
      }
      if (afterIterator.hasNext()) {
        after = afterIterator.next()
        direct.add(before, after)
        reverse.add(after, before)
      }
    }

    return of(direct, reverse)
  }

  fun <K, V> MutableMap<K, MutableList<V>>.add(key: K, value: V) {
    computeIfAbsent(key, { mutableListOf() }) += value
  }
}