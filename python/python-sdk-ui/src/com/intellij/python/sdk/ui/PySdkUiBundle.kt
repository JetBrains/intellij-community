package com.intellij.python.sdk.ui

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

object PySdkUiBundle {
  private const val BUNDLE = "messages.PySdkUiBundle"

  private val INSTANCE = DynamicBundle(PySdkUiBundle::class.java, BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return INSTANCE.getMessage(key, *params)
  }
}