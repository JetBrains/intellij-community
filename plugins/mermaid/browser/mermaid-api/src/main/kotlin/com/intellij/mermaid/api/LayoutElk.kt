package com.intellij.mermaid.api

@JsModule("@mermaid-js/layout-elk/dist/mermaid-layout-elk.esm.min.mjs")
external object LayoutElk

val LayoutElk.definitions: Array<LayoutLoaderDefinition>
  get() = asDynamic().default.unsafeCast<Array<LayoutLoaderDefinition>>()
