// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.numpy.codeInsight

import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.pyi.PyiStubSuppressor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class NumpyPyiStubsSuppressor : PyiStubSuppressor {

  override fun isIgnoredStub(file: PyiFile): Boolean {
    if (Registry.`is`("enable.numpy.pyi.stubs")) return false
    val virtualFile = file.virtualFile ?: return false

    return "/numpy/" in virtualFile.path
  }
}

