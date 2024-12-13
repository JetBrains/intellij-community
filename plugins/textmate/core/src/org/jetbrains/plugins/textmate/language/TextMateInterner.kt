package org.jetbrains.plugins.textmate.language

import java.util.concurrent.ConcurrentHashMap

interface TextMateInterner {
  fun intern(name: CharSequence): CharSequence
  fun clear()
}

class TextMateHashSetInterner : TextMateInterner {
  private val set: ConcurrentHashMap<CharSequence, CharSequence> = ConcurrentHashMap()

  override fun intern(name: CharSequence): CharSequence {
    return set.putIfAbsent(name, name) ?: name
  }

  override fun clear() {
    set.clear()
  }
}