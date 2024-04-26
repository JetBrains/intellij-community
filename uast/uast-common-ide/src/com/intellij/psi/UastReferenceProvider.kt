// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi

import com.intellij.psi.PsiReferenceService.Hints
import com.intellij.util.ProcessingContext
import org.jetbrains.uast.UElement

abstract class UastReferenceProvider(open val supportedUElementTypes: List<Class<out UElement>> = listOf(UElement::class.java)) {

  constructor(cls: Class<out UElement>) : this(listOf(cls))

  abstract fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference>

  open fun acceptsTarget(target: PsiElement): Boolean = true

  open fun acceptsHint(hints: Hints): Boolean = true
}