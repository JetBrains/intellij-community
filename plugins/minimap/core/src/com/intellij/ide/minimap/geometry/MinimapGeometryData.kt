// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.geometry

data class MinimapGeometryData(
  val minimapHeight: Int,
  val areaStart: Int,
  val areaEnd: Int,
  val thumbStart: Int,
  val thumbHeight: Int
)