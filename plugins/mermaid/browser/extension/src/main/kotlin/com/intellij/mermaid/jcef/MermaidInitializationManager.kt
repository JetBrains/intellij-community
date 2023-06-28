package com.intellij.mermaid.jcef

import com.intellij.mermaid.api.*
import kotlinx.coroutines.await
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

internal object MermaidInitializationManager {
  private var isInitialized = false

  suspend fun initializeIfNeeded() {
    if (!isInitialized) {
      isInitialized = true
      initialize()
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun initialize() {
    val time = measureTime {
      performInitialization()
    }
    console.log("Initialized mermaid in $time")
  }

  private suspend fun performInitialization() {
    console.log("Loading mermaid module")
    MermaidModuleManager.loadModule()
    console.log("Performing mermaid initialization")
    val theme = Configuration.mermaidTheme
    console.log("Applying mermaid theme: $theme")
    Mermaid.core.initialize(SimpleMermaidConfig(theme = theme))
    registerExternalDiagrams()
  }

  private suspend fun registerExternalDiagrams() {
    val externalDiagrams = arrayOf(ZenUML.definition)
//    val externalDiagrams = arrayOf<ExternalDiagramDefinition>()
    Mermaid.core.registerExternalDiagrams(externalDiagrams).await()
  }
}
