// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layers

@JvmInline
value class MinimapLayerId(val value: String) {
  init {
    require(value.isNotBlank()) { "MinimapLayerId value must not be blank" }
  }
}
