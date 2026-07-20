// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.diagnostics

import java.awt.geom.Rectangle2D

data class MinimapDiagnosticEntry(
  val rect2d: Rectangle2D.Double,
  val severity: MinimapDiagnosticSeverity,
)
