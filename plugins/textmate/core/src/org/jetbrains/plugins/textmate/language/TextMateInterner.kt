package org.jetbrains.plugins.textmate.language

interface TextMateInterner {
  fun intern(name: String): String
  fun clear()
}