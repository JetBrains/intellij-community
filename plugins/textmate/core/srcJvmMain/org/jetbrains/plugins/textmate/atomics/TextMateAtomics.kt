package org.jetbrains.plugins.textmate.atomics

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.asJavaAtomic

internal fun <T> AtomicReference<T>.updateAndGet(f: (T) -> T): T {
  return this.asJavaAtomic().updateAndGet(f)
}