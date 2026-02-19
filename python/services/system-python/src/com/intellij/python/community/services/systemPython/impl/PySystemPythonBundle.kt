package com.intellij.python.community.services.systemPython.impl

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

internal object PySystemPythonBundle {
  private const val BUNDLE = "messages.PySystemPythonBundle"

  private val INSTANCE = DynamicBundle(PySystemPythonBundle::class.java, BUNDLE)

  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return INSTANCE.getMessage(key, *params)
  }
}