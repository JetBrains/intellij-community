package com.intellij.mermaid.api

import org.w3c.dom.Element
import kotlin.js.Promise

external interface MermaidApi {
  fun render(id: String, text: String, container: Element? = definedExternally): Promise<MermaidRenderResult>

  fun parse(text: String, parseOptions: ParseOptions? = definedExternally): Promise<Boolean>

  fun initialize(options: MermaidConfig? = definedExternally)

  fun reset()

  fun globalReset()

  val defaultConfig: MermaidConfig
}
