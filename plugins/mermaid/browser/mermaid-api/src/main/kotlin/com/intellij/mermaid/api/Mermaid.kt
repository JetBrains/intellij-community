package com.intellij.mermaid.api

import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.NodeList
import kotlin.js.Promise

@JsModule("mermaid")
external object Mermaid {
  @JsName("mermaidAPI")
  val api: MermaidApi

  val startOnLoad: Boolean
  val diagrams: dynamic

  fun render(id: String, text: String, callback: RenderCallback?, svgContainingElement: Element?): String

  // declare const init: (config?: MermaidConfig, nodes?: string | HTMLElement | NodeListOf<HTMLElement>, callback?: Function) => Promise<void>;
  fun init(config: MermaidConfig?, nodes: String?, callback: () -> Unit): Promise<Unit>
  fun init(config: MermaidConfig?, nodes: HTMLElement?, callback: () -> Unit): Promise<Unit>
  fun init(config: MermaidConfig?, nodes: NodeList?, callback: () -> Unit): Promise<Unit>

  fun initialize(config: MermaidConfig)

  // declare const setParseErrorHandler: (newParseErrorHandler: (err: any, hash: any) => void) => void;
  fun setParseErrorHandler(handler: (error: Any, hash: Any) -> Unit)

  fun parse(text: String): Boolean

  // declare const registerExternalDiagrams: (diagrams: ExternalDiagramDefinition[], { lazyLoad, }?: {
  //   lazyLoad?: boolean | undefined;
  // }) => Promise<void>;

  fun registerExternalDiagrams(
    diagrams: Array<ExternalDiagramDefinition>,
    options: RegisterExternalDiagramsOptions
  ): Promise<Unit>

  fun registerExternalDiagrams(diagrams: Array<ExternalDiagramDefinition>): Promise<Unit>
}

fun Mermaid.render(id: String, text: String, svgContainingElement: Element?, callback: RenderCallback?): String {
  return render(id, text, callback, svgContainingElement)
}

fun Mermaid.render(id: String, text: String, svgContainingElement: Element?, callback: (String) -> Unit): String {
  return render(id, text, svgContainingElement) { it, _ ->  callback(it) }
}
