package com.intellij.python.typeEngine

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

object TypeEngineBundle {
  private val myDynamicBundle = DynamicBundle(this::class.java, BUNDLE)

  @NonNls
  const val BUNDLE: String = "messages.TypeEngineBundle"

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
    return myDynamicBundle.getMessage(key, *params)
  }
}
