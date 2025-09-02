package org.jetbrains.plugins.textmate.regex

internal interface TextMateThreadLocal<T> {
  fun get(): T
  fun set(value: T)
}