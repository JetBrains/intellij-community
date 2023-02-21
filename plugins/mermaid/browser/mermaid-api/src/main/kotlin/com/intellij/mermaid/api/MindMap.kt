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
@Deprecated("Should not be used since mermaid 9.4.0")
@JsModule("@mermaid-js/mermaid-mindmap/dist/mermaid-mindmap.core.mjs")
external object MindMap

@Suppress("DEPRECATION")
val MindMap.definition: ExternalDiagramDefinition
  get() = asDynamic().default.unsafeCast<ExternalDiagramDefinition>()
