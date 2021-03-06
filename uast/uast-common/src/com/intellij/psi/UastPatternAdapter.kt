// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ElementPatternCondition
import com.intellij.patterns.InitialPatternCondition
import com.intellij.util.ProcessingContext
import com.intellij.util.SharedProcessingContext
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfExpectedTypes
import java.util.*

internal class UastPatternAdapter(private val pattern: (UElement, ProcessingContext) -> Boolean,
                                  private val supportedUElementTypes: List<Class<out UElement>>) : ElementPattern<PsiElement> {
  override fun accepts(o: Any?): Boolean = accepts(o, null)

  override fun accepts(o: Any?, context: ProcessingContext?): Boolean {
    if (o !is PsiElement) return false
    if (context == null) {
      logger<UastPatternAdapter>().error("UastPatternAdapter should not be called with null context")
      return false
    }

    val uElement = getOrCreateCachedElement(o, context, supportedUElementTypes) ?: return false
    context.put(REQUESTED_PSI_ELEMENT, o)

    return pattern(uElement, context)
  }

  private val condition = ElementPatternCondition(object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
    override fun accepts(o: Any?, context: ProcessingContext?): Boolean = this@UastPatternAdapter.accepts(o, context)
  })

  override fun getCondition(): ElementPatternCondition<PsiElement> = condition

  companion object {
    private val CACHED_UAST_INJECTION_HOST: Key<Optional<UInjectionHost>> = Key.create("CACHED_UAST_INJECTION_HOST")
    private val CACHED_UAST_EXPRESSION: Key<Optional<UExpression>> = Key.create("CACHED_UAST_EXPRESSION")
    private val CACHED_UAST_ELEMENTS: Key<MutableMap<Class<out UElement>, UElement?>> = Key.create("CACHED_UAST_ELEMENTS")

    internal fun getOrCreateCachedElement(element: PsiElement,
                                          context: ProcessingContext,
                                          supportedUElementTypes: List<Class<out UElement>>): UElement? {
      if (supportedUElementTypes.size == 1) {
        val requiredType = supportedUElementTypes[0]
        val sharedContext = context.sharedContext

        when (requiredType) {
          UInjectionHost::class.java -> {
            return getCachedUElement(sharedContext, element, UInjectionHost::class.java, CACHED_UAST_INJECTION_HOST)
          }
          UExpression::class.java -> {
            return getCachedUElement(sharedContext, element, UExpression::class.java, CACHED_UAST_EXPRESSION)
          }
          else -> {
            val elementsCache = getUastElementCache(sharedContext)
            if (elementsCache.containsKey(requiredType)) { // we store nulls for non-convertable element types
              return elementsCache[requiredType]
            }
            val newElement = element.toUElement(requiredType)
            elementsCache[requiredType] = newElement
            return newElement
          }
        }
      }

      return element.toUElementOfExpectedTypes(*supportedUElementTypes.toTypedArray())
    }

    private fun getUastElementCache(sharedContext: SharedProcessingContext): MutableMap<Class<out UElement>, UElement?> {
      val existingMap = sharedContext.get(CACHED_UAST_ELEMENTS)
      if (existingMap != null) return existingMap
      // we assume that patterns are queried sequentially in the same thread
      val newMap = HashMap<Class<out UElement>, UElement?>()
      sharedContext.put(CACHED_UAST_ELEMENTS, newMap)
      return newMap
    }

    private fun <T : UElement> getCachedUElement(sharedContext: SharedProcessingContext,
                                                 element: PsiElement,
                                                 clazz: Class<T>,
                                                 cacheKey: Key<Optional<T>>): T? {
      val uElementRef = sharedContext.get(cacheKey)
      if (uElementRef != null) return uElementRef.orElse(null)

      val newUElement = element.toUElement(clazz)
      sharedContext.put(cacheKey, Optional.ofNullable(newUElement))
      return newUElement
    }
  }
}