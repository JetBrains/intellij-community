// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.model

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.editor.RangeMarker
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import java.lang.ref.SoftReference

data class MinimapStructureMarker(
  val elementReference: SoftReference<StructureViewTreeElement>,
  val rangeMarker: RangeMarker?,
  val pointer: SmartPsiElementPointer<out PsiElement>?
) {
  val element: StructureViewTreeElement?
    get() = elementReference.get()
}
