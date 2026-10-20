// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

internal class Counter<T> {
  private val map = hashMapOf<T, Int>()

  fun add(key: T?, cnt: Int = 1) {
    if (key != null) map.merge(key, cnt, Integer::sum)
  }

  operator fun get(key: T?): Int? = map[key]

  fun toMap(): Map<T, Int> = map
}