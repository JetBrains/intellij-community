// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.model

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Resolved source for a minimap structure marker.
 *
 * At least one of [psiElement] or [range] should normally be provided.
 */
data class MinimapStructureMarkerSource(
  val psiElement: PsiElement?,
  val range: TextRange?
)
