package com.intellij.mermaid.api

import org.w3c.dom.Element
import kotlin.js.Promise

external interface MermaidApi {
  fun render(id: String, text: String, callback: RenderCallback?, svgContainingElement: Element?): String?
  fun renderAsync(id: String, text: String, callback: RenderCallback?, svgContainingElement: Element?): Promise<String?>

  fun parse(text: String, parserErrorCallback: (error: String, hash: Any?) -> Unit)
  fun parse(text: String, parserErrorCallback: (error: DetailedError, hash: Any?) -> Unit)

  fun parseAsync(text: String, parserErrorCallback: (error: String, hash: Any?) -> Unit): Promise<Boolean>
  fun parseAsync(text: String, parserErrorCallback: (error: DetailedError, hash: Any?) -> Unit): Promise<Boolean>

  fun initialize(options: MermaidConfig?)

  fun reset()

  val defaultConfig: MermaidConfig
}

inline fun MermaidApi.renderAsync(
  id: String,
  text: String,
  element: Element?,
  noinline callback: RenderCallback?
): Promise<String?> {
  return renderAsync(id, text, callback, element)
}

inline fun MermaidApi.renderAsync(
  id: String,
  text: String,
  element: Element?,
  crossinline callback: (String) -> Unit
): Promise<String?> {
  return renderAsync(id, text, element) { content, _ -> callback(content) }
}
