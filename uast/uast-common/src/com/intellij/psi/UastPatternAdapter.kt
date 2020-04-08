// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi

import com.intellij.openapi.util.RecursionManager
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ElementPatternCondition
import com.intellij.patterns.InitialPatternCondition
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement

@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
@Deprecated("Use custom pattern with PsiElementPattern.Capture<PsiElement>")
internal class UastPatternAdapter(
  private val supportedUElementTypes: List<Class<out UElement>>,
  private val predicate: ((UElement, ProcessingContext) -> Boolean)
) : ElementPattern<PsiElement> {

  override fun accepts(o: Any?): Boolean = accepts(o, null)

  override fun accepts(o: Any?, context: ProcessingContext?): Boolean {
    if (o !is PsiElement) return false

    return RecursionManager.doPreventingRecursion(this, false) {
      val processingContext = context ?: ProcessingContext()
      val uElement = getOrCreateCachedElement(o, processingContext, supportedUElementTypes) ?: return@doPreventingRecursion false
      predicate.invoke(uElement, processingContext)
    } ?: false
  }

  private val condition = ElementPatternCondition(object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
    override fun accepts(o: Any?, context: ProcessingContext?): Boolean = this@UastPatternAdapter.accepts(o, context)
  })

  override fun getCondition(): ElementPatternCondition<PsiElement> = condition
}