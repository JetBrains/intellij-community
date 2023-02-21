package com.intellij.mermaid.api

import kotlinx.coroutines.await

/**
 * Handle loading of mermaid ES module.
 */
object MermaidModuleManager {
  private var instance: MermaidModule? = null

  fun obtainModule(): MermaidModule {
    val instance = instance
    checkNotNull(instance) { "Mermaid module was not loaded prior to this call" }
    return instance
  }

  suspend fun loadModule(): MermaidModule {
    // They are esm now
    val module = import("mermaid/dist/mermaid.core.mjs").await()
    val exports = module.default.unsafeCast<MermaidModule>()
    instance = exports
    return exports
  }
}
