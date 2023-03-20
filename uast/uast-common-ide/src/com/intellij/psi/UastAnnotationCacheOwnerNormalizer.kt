// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi

import com.intellij.codeInsight.AnnotationCacheOwnerNormalizer
import org.jetbrains.uast.UElement

/**
 * Normalizer for uast elements.
 * Element is normalized to java psi if possible. If not - same element is returned
 * 
 * @see AnnotationCacheOwnerNormalizer
 */
class UastAnnotationCacheOwnerNormalizer: AnnotationCacheOwnerNormalizer() {
  
  override fun doNormalize(listOwner: PsiModifierListOwner): PsiModifierListOwner {
    val javaPsi = (listOwner as? UElement)?.javaPsi ?: return listOwner
    return javaPsi as? PsiModifierListOwner ?: listOwner
  }
}