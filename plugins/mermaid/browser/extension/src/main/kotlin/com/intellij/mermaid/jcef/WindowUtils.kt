package com.intellij.mermaid.jcef

import kotlin.js.Console
import kotlin.js.Promise

inline fun <T> promise(crossinline block: ((T) -> Unit) -> Unit): Promise<T> {
  return Promise { resolve, _ -> block(resolve)  }
}

inline fun Console.time(name: String) {
  asDynamic().time(name)
}

inline fun Console.timeEnd(name: String) {
  asDynamic().timeEnd(name)
}
