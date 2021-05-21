// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.common

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.parents
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.test.common.UastMappingsAccountant.computeMappingsInSeveralViewsSimultaneously
import java.nio.file.Path


/* ------------------------------------------------------------------------------------------- */
//region UastMappingsRepository

internal typealias PsiClazz = Class<out PsiElement>
internal typealias UastClazz = Class<out UElement>

data class Location(val path: Path, val line: Int) {
  override fun toString() = "$path:$line"
}

/**
 * Representation of PSI element context for the one mapping.
 * Currently it is plain list of PSI parents of some height.
 * Do we want other more complex representations (with children, siblings)?
 *
 * @see UastMappingsAccountant.makeDefaultContextBuilder
 */
typealias PsiContext = List<PsiClazz>
typealias PsiContextBuilder = (PsiElement) -> PsiContext

typealias UastMappingsRepository<S, T> = Map3<S, PsiContext, UastClazz, Collection<T>>
typealias UastMutableMappingsRepository<S, T> = MutableMap3<S, PsiContext, UastClazz, MutableCollection<T>>

fun <S, T> mutableUastMappingsRepository() = mutableMap3Of<S, PsiContext, UastClazz, MutableCollection<T>>()

//endregion
/* ------------------------------------------------------------------------------------------- */


/* ------------------------------------------------------------------------------------------- */
//region UastMappingsAccountant

private typealias UastMappingsAccumulator<S, T> =
  (UastMutableMappingsRepository<S, T>, Path, Document, PsiElement, UastClazz, UElement?) -> Unit

/**
 * Computes mappings from PSI to UAST elements (and vice versa) on the given sources.
 *
 * @see computeMappingsInSeveralViewsSimultaneously the core function
 */
object UastMappingsAccountant {

  fun makeDefaultContextBuilder(
    contextSize: Int = 3,
    doIncludeElementItself: Boolean = false
  ): PsiContextBuilder = { psiElement ->
    psiElement.parents(false)
      .take(contextSize)
      .toMutableList()
      .map { it.javaClass }
      .let { if (doIncludeElementItself) listOf(psiElement.javaClass) + it else it }
  }

  private fun computeMappingsInSeveralViewsSimultaneously(
    sources: Iterable<Lazy<Pair<PsiFile, Path>?>>,
    accumulators: List<UastMappingsAccumulator<Any?, Any?>>,
    logger: Logger? = null
  ): List<UastMappingsRepository<Any?, Any?>> {
    val mappingsLists = List(accumulators.size) { mutableMap3Of<Any?, PsiContext, UastClazz, MutableCollection<Any?>>() }
    var failCounter = 0
    for (source in sources) {
      runReadAction {
        source.value?.let { (psiFile, path) ->
          val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile.containingFile)!!
          psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(psiElement: PsiElement) {
              /* We want to get conversions of light elements, which are impossible to get
                 by simple visitor. So on each computed `uElement` we call `javaPsi`
                 (which is potential light element) and process it too. */
              val todo = java.util.ArrayDeque(listOf(psiElement))
              val done = mutableSetOf<PsiElement>()

              while (todo.isNotEmpty()) {
                val currPsi = todo.pollFirst()!!
                done.add(currPsi)

                try {
                  for (uastType in allUElementSubtypes) {
                    val uElement = UastFacade.convertElementWithParent(currPsi, requiredType = uastType)

                    for (i in mappingsLists.indices) {
                      accumulators[i](mappingsLists[i], path, document, currPsi, uastType, uElement)
                      uElement?.javaPsi?.let { if (it !in done) todo.addLast(it) }
                    }
                  }
                }
                catch (e: Exception) {
                  failCounter++
                  logger?.warn("Exception during mappings grabbing in ${psiFile.name}\n", e)
                }
              }

              super.visitElement(psiElement)
            }
          })
        }
      }
    }

    if (failCounter > 0)
      logger?.warn("\nTOTAL files failed to analyze: $failCounter")

    return mappingsLists
  }

  private fun PsiElement.getLocation(path: Path, document: Document) =
    Location(path, textOffset.let { if (it >= 0) document.getLineNumber(it) else -1 })

  private inline fun accumulatorByPsiElements(crossinline contextBuilder: PsiContextBuilder):
    UastMappingsAccumulator<PsiClazz, PairWithFirstIdentity<UastClazz?, Location>> =
    { mappings, path, document, psiElement, requiredType, uElement ->
      mappings
        .getOrPut(psiElement.javaClass) { mutableMapOf() }
        .getOrPut(contextBuilder(psiElement)) { mutableMapOf() }
        .getOrPut(requiredType) { mutableSetOf() }
        .apply { add(PairWithFirstIdentity(uElement?.javaClass, psiElement.getLocation(path, document))) }
    }

  private inline fun accumulatorByUElements(crossinline contextBuilder: PsiContextBuilder):
    UastMappingsAccumulator<UastClazz, Location> =
    { mappings, path, document, psiElement, requiredType, uElement ->
      uElement?.let {
        mappings
          .getOrPut(it.javaClass) { mutableMapOf() }
          .getOrPut(contextBuilder(psiElement)) { mutableMapOf() }
          .putIfAbsent(requiredType, mutableSetOf(psiElement.getLocation(path, document)))
      }
    }

  @Suppress("UNCHECKED_CAST")
  fun computeMappingsByPsiElementsAndUElements(
    sources: Iterable<Lazy<Pair<PsiFile, Path>?>>,
    logger: Logger? = null,
    byPsiContextBuilder: PsiContextBuilder = makeDefaultContextBuilder(),
    byUastContextBuilder: PsiContextBuilder = makeDefaultContextBuilder(doIncludeElementItself = true)
  ): Pair<
    UastMappingsRepository<PsiClazz, PairWithFirstIdentity<UastClazz?, Location>>,
    UastMappingsRepository<UastClazz, Location>
    > {
    val (byPsiElement, byUElement) = computeMappingsInSeveralViewsSimultaneously(
      sources,
      listOf(accumulatorByPsiElements(byPsiContextBuilder) as UastMappingsAccumulator<Any?, Any?>,
             accumulatorByUElements(byUastContextBuilder) as UastMappingsAccumulator<Any?, Any?>),
      logger)
    return Pair(
      byPsiElement as UastMappingsRepository<PsiClazz, PairWithFirstIdentity<UastClazz?, Location>>,
      byUElement as UastMappingsRepository<UastClazz, Location>)
  }
}

//endregion
/* ------------------------------------------------------------------------------------------- */


/* ------------------------------------------------------------------------------------------- */
//region Utils

/**
 * Plain [Pair] but the second element does not participate in comparison.
 */
data class PairWithFirstIdentity<out F, out S>(val first: F, val second: S) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PairWithFirstIdentity<*, *>
    if (first != other.first) return false

    return true
  }

  override fun hashCode() = first?.hashCode() ?: 0
}

//endregion
/* ------------------------------------------------------------------------------------------- */