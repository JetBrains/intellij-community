package org.jetbrains.plugins.textmate.language

import java.util.concurrent.ConcurrentHashMap

class TextMateConcurrentMapInterner : TextMateInterner {
  private val set: ConcurrentHashMap<String, String> = ConcurrentHashMap()

  override fun intern(name: String): String {
    return set.putIfAbsent(name, name) ?: name
  }

  override fun clear() {
    set.clear()
  }
}