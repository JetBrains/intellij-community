package org.jetbrains.plugins.textmate.concurrent

internal interface TextMateThreadLocal<T> {
  fun get(): T
  fun set(value: T)
}