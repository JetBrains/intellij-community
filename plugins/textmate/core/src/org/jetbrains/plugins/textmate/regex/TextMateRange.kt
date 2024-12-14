package org.jetbrains.plugins.textmate.regex

data class TextMateRange(@JvmField val start: Int,
                         @JvmField val end: Int) {
  val isEmpty: Boolean
    get() = start == end

  val length: Int
    get() = end - start
}
