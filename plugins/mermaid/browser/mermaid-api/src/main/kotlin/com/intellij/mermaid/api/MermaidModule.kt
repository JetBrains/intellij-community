package com.intellij.mermaid.api

import org.w3c.dom.Element
import kotlin.js.Promise

external interface MermaidModule {
  @JsName("mermaidAPI")
  val api: MermaidApi

  var startOnLoad: Boolean

  val diagrams: dynamic

  // declare const render: (id: string, text: string, container?: Element) => Promise<RenderResult>;
  fun render(id: String, text: String, container: Element? = definedExternally): Promise<MermaidRenderResult>

  fun initialize(config: MermaidConfig)

  // declare const setParseErrorHandler: (newParseErrorHandler: (err: any, hash: any) => void) => void;
  fun setParseErrorHandler(handler: (error: Any, hash: Any) -> Unit)

  // declare const parse: (text: string, parseOptions?: ParseOptions) => Promise<boolean | void>;
  fun parse(text: String, options: ParseOptions? = definedExternally): Promise<Boolean>

  // declare const registerExternalDiagrams: (diagrams: ExternalDiagramDefinition[], { lazyLoad, }?: {
  //   lazyLoad?: boolean | undefined;
  // }) => Promise<void>;
  fun registerExternalDiagrams(
    diagrams: Array<ExternalDiagramDefinition>,
    options: RegisterExternalDiagramsOptions
  ): Promise<Unit>

  fun registerExternalDiagrams(diagrams: Array<ExternalDiagramDefinition>): Promise<Unit>
}
