package com.intellij.mermaid.jcef

import com.intellij.mermaid.api.Mermaid
import com.intellij.mermaid.api.MindMap
import com.intellij.mermaid.api.SimpleMermaidConfig
import com.intellij.mermaid.api.definition
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
    console.log("Performing mermaid initialization")
    val theme = Configuration.mermaidTheme
    console.log("Applying mermaid theme: $theme")
    Mermaid.api.initialize(SimpleMermaidConfig(theme = theme))
    registerExternalDiagrams()
  }

  private suspend fun registerExternalDiagrams() {
    val externalDiagrams = arrayOf(MindMap.definition)
    Mermaid.registerExternalDiagrams(externalDiagrams).await()
  }
}
