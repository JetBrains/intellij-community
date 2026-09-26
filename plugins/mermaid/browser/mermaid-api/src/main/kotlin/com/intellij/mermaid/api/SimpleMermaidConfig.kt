package com.intellij.mermaid.api

@OptIn(ExperimentalJsExport::class)
@JsExport
data class SimpleMermaidConfig(
  override val theme: String? = undefined,
  override val darkMode: Boolean? = undefined,
  override val fontFamily: String? = undefined,
  override val altFontFamily: String? = undefined,
  override val startOnLoad: Boolean? = undefined,
  override val wrap: Boolean? = undefined,
  override val fontSize: Int? = undefined
): MermaidConfig
