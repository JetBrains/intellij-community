package org.jetbrains.plugins.textmate.regex

data class TextMateRange(val start: Int,
                         val end: Int) {
  val isEmpty: Boolean
    get() = start == end

  val length: Int
    get() = end - start
}
