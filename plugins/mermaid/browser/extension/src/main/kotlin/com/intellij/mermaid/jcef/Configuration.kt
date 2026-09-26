package com.intellij.mermaid.jcef

import kotlinx.browser.window
import org.w3c.dom.get

internal object Configuration {
  val mermaidTheme: String?
    get() = window["mermaidTheme"] as? String ?: undefined
}
