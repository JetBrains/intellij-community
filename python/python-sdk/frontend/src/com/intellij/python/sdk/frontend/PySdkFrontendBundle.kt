package com.intellij.python.sdk.frontend

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

object PySdkFrontendBundle {
  private const val BUNDLE = "messages.PySdkFrontendBundle"

  private val INSTANCE = DynamicBundle(PySdkFrontendBundle::class.java, BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return INSTANCE.getMessage(key, *params)
  }
}