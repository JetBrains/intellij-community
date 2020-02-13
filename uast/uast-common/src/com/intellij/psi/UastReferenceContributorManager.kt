// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.util.ProcessingContext
import gnu.trove.THashMap
import org.jetbrains.uast.UElement

/**
 * Groups all UAST-based reference contributors by chunks with the same priority and supported UElement types.
 * Enables proper caching of UElement for underlying reference contributors.
 */
internal class UastReferenceContributorManager(private val registrar: PsiReferenceRegistrar) {
  private val chunks: MutableMap<ChunkTag, UastReferenceContributorChunk> = THashMap()

  fun register(pattern: (UElement, ProcessingContext) -> Boolean,
               provider: UastReferenceProvider,
               priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
    val chunk = chunks.getOrPut(ChunkTag(priority, provider.supportedUElementTypes)) {
      val newChunk = UastReferenceContributorChunk(provider.supportedUElementTypes)
      registrar.registerReferenceProvider(adaptPattern(anyUElement, provider.supportedUElementTypes), newChunk, priority)
      newChunk
    }
    chunk.register(pattern, provider)
  }

  companion object {
    private val anyUElement: (UElement, ProcessingContext) -> Boolean = { _, _ -> true }
    private val MANAGER_KEY: Key<UastReferenceContributorManager> = Key.create("UastReferenceContributorManager")

    fun get(registrar: PsiReferenceRegistrar): UastReferenceContributorManager {
      val userData = registrar.getUserData(MANAGER_KEY)
      if (userData != null) return userData

      val adapter = UastReferenceContributorManager(registrar)
      registrar.putUserData(MANAGER_KEY, adapter)
      return adapter
    }
  }
}

private class UastReferenceContributorChunk(private val supportedUElementTypes: List<Class<out UElement>>) : PsiReferenceProvider() {
  private val providers: MutableList<ProviderRegistration> = mutableListOf()

  fun register(pattern: (UElement, ProcessingContext) -> Boolean,
               provider: UastReferenceProvider) {
    providers.add(ProviderRegistration(pattern, provider))
  }

  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val uElement = getOrCreateCachedElement(element, context, supportedUElementTypes) ?: return PsiReference.EMPTY_ARRAY

    val references = providers.asSequence()
      .filter { it.provider.acceptsTarget(element) && it.pattern.invoke(uElement, context) }
      .flatMap {
        val referencesByElement = it.provider.getReferencesByElement(uElement, context)

        if (referencesByElement.isNotEmpty()
            && (ApplicationManager.getApplication().isUnitTestMode || ApplicationManager.getApplication().isInternal)) {
          for (reference in referencesByElement) {
            if (reference.element !== element)
              throw AssertionError(
                """reference $reference was created for $element but targets ${reference.element}, provider ${it.provider}"""
              )
          }
        }

        referencesByElement.asSequence()
      }
      .toList()
    if (references.isEmpty()) return PsiReference.EMPTY_ARRAY

    return references.toTypedArray()
  }
}

private data class ChunkTag(val priority: Double,
                            val supportedUElementTypes: List<Class<out UElement>>)

private class ProviderRegistration(val pattern: (UElement, ProcessingContext) -> Boolean,
                                   val provider: UastReferenceProvider) {
  override fun toString(): String {
    return "provider(${provider})"
  }
}