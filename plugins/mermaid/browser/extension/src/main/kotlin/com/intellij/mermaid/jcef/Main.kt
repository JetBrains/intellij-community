package com.intellij.mermaid.jcef

import kotlinx.browser.window
import org.w3c.dom.url.URLSearchParams

private fun isStandaloneEnvironment(): Boolean {
  val parameters = URLSearchParams(window.location.search)
  return parameters.has("isStandaloneViewer")
}

suspend fun main() {
  MermaidInitializationManager.initializeIfNeeded()
  when {
    isStandaloneEnvironment() -> viewerMain()
    else -> markdownExtensionMain()
  }
}
