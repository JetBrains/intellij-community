// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.model

data class MinimapHiddenInterval(
  val startLine: Int,
  val endLine: Int,
  val projectedLine: Int,
  val hiddenLineCount: Int,
)
