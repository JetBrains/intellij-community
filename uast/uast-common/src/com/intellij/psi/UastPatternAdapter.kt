// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi

import com.intellij.openapi.util.RecursionManager
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ElementPatternCondition
import com.intellij.patterns.InitialPatternCondition
import com.intellij.util.ProcessingContext
import org.jetbrains.uast.UElement

internal class UastPatternAdapter(
  private val supportedUElementTypes: List<Class<out UElement>>,
  val predicate: ((UElement, ProcessingContext) -> Boolean)?
) : ElementPattern<PsiElement> {

  constructor(supportedUElementTypes: List<Class<out UElement>>) : this(supportedUElementTypes, null)

  override fun accepts(o: Any?): Boolean = accepts(o, null)

  override fun accepts(o: Any?, context: ProcessingContext?): Boolean {
    if (o !is PsiElement) return false

    return RecursionManager.doPreventingRecursion(this, false) {
      val uElement = getOrCreateCachedElement(o, context, supportedUElementTypes)
      if (uElement == null) return@doPreventingRecursion false

      val currentContext = (context ?: ProcessingContext()).apply { put(REQUESTED_PSI_ELEMENT, o) }
      predicate == null || predicate.invoke(uElement, currentContext)
    } ?: false
  }

  private val condition = ElementPatternCondition(object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
    override fun accepts(o: Any?, context: ProcessingContext?): Boolean = this@UastPatternAdapter.accepts(o, context)
  })

  override fun getCondition(): ElementPatternCondition<PsiElement> = condition
}