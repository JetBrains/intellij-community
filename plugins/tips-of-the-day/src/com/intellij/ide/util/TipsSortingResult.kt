// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

internal class TipsSortingResult private constructor(
  val tips: List<TipAndTrickBean>,
  val algorithm: String,
  val version: String?,
) {
  companion object {
    @JvmStatic
    @JvmOverloads
    fun create(tips: List<TipAndTrickBean>, algorithm: String = "unknown", version: String? = null): TipsSortingResult {
      return TipsSortingResult(tips, algorithm, version)
    }
  }

  override fun toString(): String {
    return "TipsSortingResult(tips=$tips, algorithm='$algorithm', version=$version)"
  }
}