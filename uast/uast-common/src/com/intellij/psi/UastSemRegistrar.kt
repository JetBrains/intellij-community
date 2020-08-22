// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UastSemRegistrar")
@file:ApiStatus.Experimental

package com.intellij.psi

import com.intellij.patterns.ElementPattern
import com.intellij.semantic.SemElement
import com.intellij.semantic.SemKey
import com.intellij.semantic.SemRegistrar
import com.intellij.util.NullableFunction
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UExpression

fun <T : SemElement> SemRegistrar.registerUastSemElementProvider(
  key: SemKey<T>,
  pattern: ElementPattern<out UExpression>,
  provider: (UExpression) -> T?
) {
  val semProvider = UastSemElementProvider(pattern, provider)
  registerSemElementProvider(key, semProvider.pattern, semProvider.provider)
}

private class UastSemElementProvider<T>(private val uastPattern: ElementPattern<out UExpression>, semProvider: (UExpression) -> T?) {
  val pattern: ElementPattern<PsiElement> = UastPatternWrapper()

  val provider: NullableFunction<PsiElement, T> = NullableFunction {
    //TODO: Use ProcessingContext correctly
    val uElement = getOrCreateCachedElement(it, ProcessingContext(), listOf(UExpression::class.java)) as? UExpression ?: return@NullableFunction null
    semProvider(uElement)
  }

  private inner class UastPatternWrapper : ElementPattern<PsiElement> by UastPatternAdapter(
    uastPattern::accepts,
    listOf(UExpression::class.java)
  ) {
    override fun accepts(o: Any?): Boolean {
      if (o !is PsiElement) return false
      return accepts(o, ProcessingContext())
    }
  }
}