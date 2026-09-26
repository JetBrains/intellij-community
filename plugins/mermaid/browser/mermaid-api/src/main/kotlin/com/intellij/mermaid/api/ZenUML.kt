package com.intellij.mermaid.api

/**
 * Because of some weird stuff they do in the `package.json` exports section,
 * we have to map the whole module object to a dedicated class.
 *
 * The corresponding module is defined like this:
 * ```javascript
 * const plugin = {
 *   id,
 *   detector,
 *   loader
 * };
 * export {
 *   plugin as default
 * };
 * ```
 *
 * For some reason this was the only way for compiler to generate correct code.
 */
@JsModule("@mermaid-js/mermaid-zenuml/dist/mermaid-zenuml.esm.min.mjs")
external object ZenUML

val ZenUML.definition: ExternalDiagramDefinition
  get() = asDynamic().default.unsafeCast<ExternalDiagramDefinition>()
