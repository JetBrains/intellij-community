package com.intellij.mermaid.api

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

  fun loadModule(): MermaidModule {
    check(instance == null) { "Mermaid module should be loaded only once" }
    val exports = RawMermaidModule.default
    exports.startOnLoad = false
    instance = exports
    return exports
  }
}
