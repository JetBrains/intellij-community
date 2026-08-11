package com.intellij.python.ruff

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

object RuffBundle {
  private val myDynamicBundle = DynamicBundle(this::class.java, BUNDLE)

  @NonNls
  const val BUNDLE: String = "messages.RuffBundle"

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
    return myDynamicBundle.getMessage(key, *params)
  }
}
