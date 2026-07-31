package com.intellij.python.pyproject.model.api

import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.jetbrains.python.sdk.PythonInterpreter
import com.jetbrains.python.sdk.getSdkAPI

fun PythonInterpreter.getPyProjectManager(): PyProjectManager {
  @Suppress("DEPRECATION") // This is a pycharm-specific low-level implementation detail, hence ok
  return PyProjectManager.forSdk(getSdkAPI())
}
