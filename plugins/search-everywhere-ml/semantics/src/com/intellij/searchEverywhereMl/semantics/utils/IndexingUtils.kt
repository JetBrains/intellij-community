package com.intellij.searchEverywhereMl.semantics.utils

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.searchEverywhereMl.semantics.services.LocalEmbeddingServiceProvider
import kotlin.math.sqrt

private const val SEPARATOR = "~"

fun splitIdentifierIntoTokens(initialScope: String): List<String> {
  var scope = initialScope
  for (reg in arrayOf("(?<=[A-Za-z])(?=[A-Z][a-z])", "[^\\w\\s]", "[_\\-]")) {
    scope = scope.replace(reg.toRegex(), SEPARATOR)
  }

  val initialScopeArray = scope.split(SEPARATOR)

  return buildList {
    for (elem in initialScopeArray) {
      if (elem.isEmpty()) continue
      var prev = Character.isUpperCase(elem[elem.length - 1])
      var part = elem

      for (i in elem.length - 2 downTo 0) {
        val nextElem = Character.isUpperCase(part[i])
        if (prev xor nextElem) {
          part = part.substring(0, i + if (prev) 1 else 0) + SEPARATOR + part.substring(i + if (prev) 1 else 0)
          prev = nextElem
        }
      }

      part.split(SEPARATOR)
        .flatMap { it.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)".toRegex()) }
        .filterNot(String::isEmpty)
        .map { it.lowercase() }
        .forEach(this::add)
    }
  }
}

fun generateEmbedding(indexableRepresentation: String): FloatTextEmbedding? {
  return generateEmbeddings(listOf(indexableRepresentation))?.single()
}

fun generateEmbeddings(texts: List<String>): List<FloatTextEmbedding>? {
  return ProgressManager.getInstance().runProcess<List<FloatTextEmbedding>?>(
    {
      val embeddingService = LocalEmbeddingServiceProvider.getInstance().getServiceBlocking() ?: return@runProcess null
      runBlockingCancellable { embeddingService.embed(texts) }.map { it.normalized() }
    },
    EmptyProgressIndicator()
  )
}

fun FloatTextEmbedding.normalized(): FloatTextEmbedding {
  val norm = sqrt(this * this)
  return FloatTextEmbedding(this.values.map { it / norm }.toFloatArray())
}

fun checkIntegrationTestMode(): Boolean {
  return System.getProperty("idea.is.integration.test") == "true"
}
