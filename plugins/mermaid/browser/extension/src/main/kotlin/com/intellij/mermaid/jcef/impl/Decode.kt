package com.intellij.mermaid.jcef.impl

import kotlinx.browser.window
import org.khronos.webgl.Uint8Array

private external class TextDecoder {
  fun decode(iterable: dynamic): String
}

internal fun decode(content: String): String {
  val bytes = window.atob(content)
  val fixed = bytes.map { it.code.toByte() }.toTypedArray()
  val decoder = TextDecoder()
  return decoder.decode(Uint8Array(fixed))
}
