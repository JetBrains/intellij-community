// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UastReferenceRegistrar")

package com.intellij.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.patterns.uast.UElementPattern
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.toUElementOfExpectedTypes
import java.util.concurrent.ConcurrentHashMap

private val CONTRIBUTOR_CHUNKS_KEY: Key<MutableMap<ChunkTag, UastReferenceContributorChunk>> = Key.create(
  "uast.psiReferenceContributor.chunks")

/**
 * Groups all UAST-based reference providers by chunks with the same priority and supported UElement types.
 */
fun PsiReferenceRegistrar.registerUastReferenceProvider(pattern: (UElement, ProcessingContext) -> Boolean,
                                                        provider: UastReferenceProvider,
                                                        priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
  // important: here we rely on the fact that all reference contributors run in a single-thread
  val registrar = this
  val chunks = getContributorChunks(registrar)

  val chunk = chunks.getOrPut(ChunkTag(priority, provider.supportedUElementTypes)) {
    val newChunk = UastReferenceContributorChunk(provider.supportedUElementTypes)
    registrar.registerReferenceProvider(uastTypePattern(provider.supportedUElementTypes), newChunk, priority)
    newChunk
  }
  chunk.register(pattern, provider)
}

private fun getContributorChunks(registrar: PsiReferenceRegistrar): MutableMap<ChunkTag, UastReferenceContributorChunk> {
  val existingChunks = registrar.getUserData(CONTRIBUTOR_CHUNKS_KEY)
  if (existingChunks != null) return existingChunks

  val newChunks: MutableMap<ChunkTag, UastReferenceContributorChunk> = ConcurrentHashMap()
  registrar.putUserData(CONTRIBUTOR_CHUNKS_KEY, newChunks)
  // reset cache on extension point removal IDEA-235191
  PsiReferenceContributor.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<KeyedLazyInstance<PsiReferenceContributor>> {
    override fun extensionRemoved(extension: KeyedLazyInstance<PsiReferenceContributor>, pluginDescriptor: PluginDescriptor) {
      newChunks.clear()
    }
  }, ApplicationManager.getApplication())
  return newChunks
}

fun PsiReferenceRegistrar.registerUastReferenceProvider(pattern: ElementPattern<out UElement>,
                                                        provider: UastReferenceProvider,
                                                        priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
  this.registerUastReferenceProvider(pattern::accepts, provider, priority)
}

fun uastInjectionHostReferenceProvider(provider: (UExpression, PsiLanguageInjectionHost) -> Array<PsiReference>): UastInjectionHostReferenceProvider =
  object : UastInjectionHostReferenceProvider() {
    override fun getReferencesForInjectionHost(uExpression: UExpression,
                                               host: PsiLanguageInjectionHost,
                                               context: ProcessingContext): Array<PsiReference> = provider(uExpression, host)

    override fun toString(): String = "uastInjectionHostReferenceProvider($provider)"
  }

fun <T : UElement> uastReferenceProvider(cls: Class<T>, provider: (T, PsiElement) -> Array<PsiReference>): UastReferenceProvider =
  object : UastReferenceProvider(cls) {

    override fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> =
      provider(cls.cast(element), context[REQUESTED_PSI_ELEMENT])

    override fun toString(): String = "uastReferenceProvider($provider)"
  }

inline fun <reified T : UElement> uastReferenceProvider(noinline provider: (T, PsiElement) -> Array<PsiReference>): UastReferenceProvider =
  uastReferenceProvider(T::class.java, provider)

private val cachedUElement = Key.create<UElement>("UastReferenceRegistrar.cachedUElement")
internal val REQUESTED_PSI_ELEMENT = Key.create<PsiElement>("REQUESTED_PSI_ELEMENT")
internal val USAGE_PSI_ELEMENT = Key.create<PsiElement>("USAGE_PSI_ELEMENT")

internal fun getOrCreateCachedElement(element: PsiElement,
                                      context: ProcessingContext,
                                      supportedUElementTypes: List<Class<out UElement>>): UElement? {
  val existingElement = element as? UElement ?: context.get(cachedUElement)
  if (existingElement != null) return existingElement

  val uElement = element.toUElementOfExpectedTypes(*supportedUElementTypes.toTypedArray()) ?: return null
  context.put(cachedUElement, uElement)
  return uElement
}

internal fun uastTypePattern(supportedUElementTypes: List<Class<out UElement>>): ElementPattern<out PsiElement> {
  val uastTypePattern = UElementTypePatternAdapter(supportedUElementTypes)

  // optimisation until IDEA-211738 is implemented
  if (supportedUElementTypes == listOf(UInjectionHost::class.java)) {
    return StandardPatterns.instanceOf(PsiLanguageInjectionHost::class.java).and(uastTypePattern)
  }

  return uastTypePattern
}

@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
@Deprecated("Use custom pattern with PsiElementPattern.Capture<PsiElement>")
fun ElementPattern<out UElement>.asPsiPattern(vararg supportedUElementTypes: Class<out UElement>): ElementPattern<PsiElement> = UastPatternAdapter(
  if (supportedUElementTypes.isNotEmpty()) supportedUElementTypes.toList() else listOf(UElement::class.java),
  this::accepts
)

/**
 * Creates UAST reference provider that accepts additional PSI element that could be either the same as reference PSI element or reference
 * element that is used in the same file and satisfy usage pattern.
 *
 * @see registerReferenceProviderByUsage
 */
@ApiStatus.Experimental
fun uastReferenceProviderByUsage(provider: (UExpression, referencePsi: PsiLanguageInjectionHost, usagePsi: PsiElement) -> Array<PsiReference>): UastReferenceProvider =
  object : UastReferenceProvider(UInjectionHost::class.java) {

    override fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> {
      val uLiteral = element as? UExpression ?: return PsiReference.EMPTY_ARRAY
      val host = context[REQUESTED_PSI_ELEMENT] as? PsiLanguageInjectionHost ?: return PsiReference.EMPTY_ARRAY
      val usagePsi = context[USAGE_PSI_ELEMENT] ?: context[REQUESTED_PSI_ELEMENT]

      return provider(uLiteral, host, usagePsi)
    }

    override fun toString(): String = "uastByUsageReferenceProvider($provider)"
  }

/**
 * Registers a provider that will be called on the expressions that directly satisfy the [usagePattern] or at least one of the expression
 * usages satisfies the pattern if it was assigned to a variable. The provider will search for usages of variables only for expressions that
 * satisfy [expressionPattern]. There are standard expression patterns for usage search: [uInjectionHostInVariable] and [uExpressionInVariable].
 *
 * Consider using [uastReferenceProviderByUsage] if you need to obtain additional context from a usage place.
 */
@ApiStatus.Experimental
fun PsiReferenceRegistrar.registerReferenceProviderByUsage(expressionPattern: UElementPattern<*, *>,
                                                           usagePattern: UElementPattern<*, *>,
                                                           provider: UastReferenceProvider,
                                                           priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
  this.registerUastReferenceProvider(usagePattern, provider, priority)

  if (Registry.`is`("uast.references.by.usage", true)) {
    this.registerUastReferenceProvider(expressionPattern, UastReferenceByUsageAdapter(usagePattern, provider), priority)
  }
}