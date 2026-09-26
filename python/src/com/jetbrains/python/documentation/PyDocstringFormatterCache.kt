// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.containers.SLRUMap
import com.jetbrains.python.psi.LanguageLevel

/**
 * Caches the output of the external docstring formatter (see [PyRuntimeDocstringFormatter]).
 *
 * Rendering Python documentation reformats docstrings by spawning an external interpreter process,
 * which is expensive and is repeated on every documentation render (the documentation popup
 * re-requests docs frequently). The formatter output is effectively a pure function of the input
 * text, the format and the flags for a given SDK, so identical requests can reuse a previous result
 * instead of spawning another process.
 */
@Service(Service.Level.APP)
class PyDocstringFormatterCache {

  data class Key(
    val sdkHome: String,
    val languageLevel: LanguageLevel,
    val formatterCommand: String,
    val flags: List<String>,
    val input: String,
  )

  private val cache = SLRUMap<Key, String>(PROTECTED_QUEUE_SIZE, PROBATIONAL_QUEUE_SIZE)

  fun getOrCompute(key: Key, compute: () -> String?): String? {
    synchronized(cache) {
      cache.get(key)
    }?.let { return it }

    val computed = compute() ?: return null

    synchronized(cache) {
      cache.put(key, computed)
    }
    return computed
  }

  companion object {
    private const val PROTECTED_QUEUE_SIZE = 64
    private const val PROBATIONAL_QUEUE_SIZE = 64

    @JvmStatic
    fun getInstance(): PyDocstringFormatterCache = service()
  }
}
