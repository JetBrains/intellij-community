// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UastSemRegistrar")
@file:ApiStatus.Experimental

package com.intellij.psi

import com.intellij.patterns.ElementPattern
import com.intellij.psi.UastPatternAdapter.Companion.getOrCreateCachedElement
import com.intellij.semantic.SemElement
import com.intellij.semantic.SemKey
import com.intellij.semantic.SemRegistrar
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.expressions.UInjectionHost
import java.util.function.BiFunction

abstract class UastSemProvider<T : SemElement>(val supportedUElementTypes: List<Class<out UElement>>) {
  constructor(cls: Class<out UElement>) : this(listOf(cls))

  abstract fun getSemElements(element: UElement, psi: PsiElement, context: ProcessingContext): Collection<T>
}

fun <T : SemElement, U : UElement> SemRegistrar.registerUastSemProvider(
  key: SemKey<T>,
  pattern: ElementPattern<out U>,
  provider: UastSemProvider<T>
) {
  this.registerSemProvider(key, BiFunction { element, context ->
    if (provider.supportedUElementTypes.size == 1
        && provider.supportedUElementTypes[0] == UInjectionHost::class.java
        && element !is PsiLanguageInjectionHost) {
      return@BiFunction emptyList()
    }

    val uElement = getOrCreateCachedElement(element, context, provider.supportedUElementTypes)
    if (uElement != null && pattern.accepts(uElement, context)) {
      provider.getSemElements(uElement, element, context)
    }
    else {
      emptyList()
    }
  })
}

fun <U : UElement, T : SemElement> uastSemElementProvider(cls: Class<U>, provider: (U, PsiElement) -> T?): UastSemProvider<T> =
  uastSemElementProvider(listOf(cls), provider)

fun <U : UElement, T : SemElement> uastSemElementProvider(supportedUElementTypes: List<Class<out U>>, provider: (U, PsiElement) -> T?): UastSemProvider<T> {
  return object : UastSemProvider<T>(supportedUElementTypes) {
    override fun getSemElements(element: UElement, psi: PsiElement, context: ProcessingContext): Collection<T> {
      @Suppress("UNCHECKED_CAST")
      return provider.invoke(element as U, psi)?.let { listOf(it) } ?: emptyList()
    }
  }
}

inline fun <reified U : UElement, T : SemElement> uastSemElementProvider(noinline provider: (U, PsiElement) -> T?): UastSemProvider<T> =
  uastSemElementProvider(U::class.java, provider)

fun <U : UElement, T : SemElement> uastRepeatableSemElementProvider(cls: Class<U>,
                                                                    provider: (U, PsiElement) -> Collection<T>): UastSemProvider<T> {
  return object : UastSemProvider<T>(cls) {
    override fun getSemElements(element: UElement, psi: PsiElement, context: ProcessingContext): Collection<T> {
      return provider.invoke(cls.cast(element), psi)
    }
  }
}

inline fun <reified U : UElement, T : SemElement> uastRepeatableSemElementProvider(noinline provider: (U, PsiElement) -> Collection<T>): UastSemProvider<T> =
  uastRepeatableSemElementProvider(U::class.java, provider)