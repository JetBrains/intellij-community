package org.jetbrains.plugins.textmate.regex

internal fun <T> createTextMateThreadLocalJvm(): TextMateThreadLocal<T> {
  return object : TextMateThreadLocal<T> {
    private val threadLocal = ThreadLocal<T>()

    override fun get(): T {
      return threadLocal.get()
    }

    override fun set(value: T) {
      threadLocal.set(value)
    }
  }
}