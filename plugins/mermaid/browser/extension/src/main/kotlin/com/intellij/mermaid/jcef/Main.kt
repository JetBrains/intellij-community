package com.intellij.mermaid.jcef

import com.intellij.mermaid.api.commonJsRequire
import kotlinx.browser.window
import org.w3c.dom.url.URLSearchParams

private fun isStandaloneEnvironment(): Boolean {
  val parameters = URLSearchParams(window.location.search)
  return parameters.has("isStandaloneViewer")
}

private fun loadRequiredResources() {
  commonJsRequire("@fortawesome/fontawesome-free/css/all.min.css")
}

suspend fun main() {
  loadRequiredResources()
  MermaidInitializationManager.initializeIfNeeded()
  when {
    isStandaloneEnvironment() -> viewerMain()
    else -> markdownExtensionMain()
  }
}
