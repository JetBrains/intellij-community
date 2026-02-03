package com.jetbrains.python.numpy.codeInsight

import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.pyi.PyiStubSuppressor

class NumpyPyiStubsSuppressor : PyiStubSuppressor {

  override fun isIgnoredStub(file: PyiFile): Boolean {
    if (Registry.`is`("enable.numpy.pyi.stubs")) return false
    val virtualFile = file.virtualFile ?: return false

    return "/numpy/" in virtualFile.path
  }
}

