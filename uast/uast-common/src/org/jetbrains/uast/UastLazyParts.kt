// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val UNINITIALIZED_UAST_PART = object : Any() {
  override fun toString(): String {
    return "UNINITIALIZED_UAST_PART"
  }
}

/**
 * More lightweight replacement for `lazy { }` inside UAST element implementations.
 * It is used to decrease memory allocations during `toUElement()` conversions.
 */
@ApiStatus.Internal
@Suppress("unused")
class UastLazyPart<T> {
  var value: Any? = UNINITIALIZED_UAST_PART

  override fun toString(): String {
    if (value == UNINITIALIZED_UAST_PART) return "UastLazyPart()"

    return "UastLazyPart($value)"
  }
}

@ApiStatus.Internal
@Suppress("UNCHECKED_CAST")
inline fun <T> UastLazyPart<T>.getOrBuild(initializer: () -> T): T {
  var current = value
  if (current == UNINITIALIZED_UAST_PART) {
    current = initializer.invoke()
    value = current
  }
  return current as T
}