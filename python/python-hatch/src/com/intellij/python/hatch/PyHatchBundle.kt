package com.intellij.python.hatch

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

internal object PyHatchBundle {
  private const val BUNDLE = "messages.PyHatchBundle"

  private val INSTANCE = DynamicBundle(PyHatchBundle::class.java, BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return INSTANCE.getMessage(key, *params)
  }
}