// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi

import com.intellij.util.ProcessingContext
import org.jetbrains.uast.UElement

abstract class UastReferenceProvider(open val supportedUElementTypes: List<Class<out UElement>> = listOf(UElement::class.java)) {

  constructor(cls: Class<out UElement>) : this(listOf(cls))

  abstract fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference>

  open fun acceptsTarget(target: PsiElement): Boolean = true
}