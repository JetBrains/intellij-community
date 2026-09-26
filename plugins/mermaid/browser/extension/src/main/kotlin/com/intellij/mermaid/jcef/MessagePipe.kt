package com.intellij.mermaid.jcef

import org.w3c.dom.Window
import org.w3c.dom.get

external interface MessagePipe {
  fun post(event: String, data: String)
}

@Suppress("USELESS_CAST")
private val Window.intellijToolsNamespace: Any?
  get() = (window["__IntelliJTools"] as? Any)?.takeIf { it != undefined }

fun Window.obtainMessagePipe(): MessagePipe? {
  val namespace = intellijToolsNamespace ?: return null
  val pipe = namespace.asDynamic()["messagePipe"]
  if (pipe == null || pipe == undefined) {
    return null
  }
  @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
  return pipe as? MessagePipe
}
