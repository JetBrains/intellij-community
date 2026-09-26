package com.intellij.python.pyproject.model.api

import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI

fun PythonInterpreter.getPyProjectManager(): PyProjectManager {
  @Suppress("DEPRECATION") // This is a pycharm-specific low-level implementation detail, hence ok
  return PyProjectManager.forSdk(getSdkAPI())
}
