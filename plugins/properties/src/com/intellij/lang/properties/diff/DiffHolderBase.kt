// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.diff

import com.intellij.diff.util.Side

abstract class DiffHolderBase<V>(private val left: V, private val right: V) {
  operator fun get(side: Side): V = when(side)
  {
    Side.LEFT -> left
    Side.RIGHT -> right
  }
}