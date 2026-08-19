// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layout

import com.intellij.ide.minimap.model.MinimapSourceSoftWrap

data class MinimapLineProjectionData(
  val lineSpanOverrides: Map<Int, Int>,
  val sourceSoftWrapsByLine: Map<Int, List<MinimapSourceSoftWrap>>,
) {
  companion object {
    val EMPTY: MinimapLineProjectionData = MinimapLineProjectionData(emptyMap(), emptyMap())
  }
}
