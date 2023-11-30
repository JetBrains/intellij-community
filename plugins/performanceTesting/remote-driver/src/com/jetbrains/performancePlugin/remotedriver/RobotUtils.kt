package com.jetbrains.performancePlugin.remotedriver

import java.time.Duration

fun waitFor(
  duration: Duration = Duration.ofSeconds(5),
  interval: Duration = Duration.ofSeconds(2),
  errorMessage: String = "",
  condition: () -> Boolean
) {
  val endTime = System.currentTimeMillis() + duration.toMillis()
  var now = System.currentTimeMillis()
  while (now < endTime && condition().not()) {
    Thread.sleep(interval.toMillis())
    now = System.currentTimeMillis()
  }
  if (condition().not()) {
    throw IllegalStateException("Timeout($duration): $errorMessage")
  }
}

internal class LruCache<K, V>(private val maxEntries: Int = 1000) : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
  override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
    return this.size > maxEntries
  }
}