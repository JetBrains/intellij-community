// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext

interface PyUnusedLocalInspectionSkipper {
  fun skip(element: PyFunction, context: TypeEvalContext): Boolean

  companion object {
    fun skip(element: PyFunction,
             context: TypeEvalContext) = EP.extensions.any { it.skip(element, context) }
    val EP = ExtensionPointName.create<PyUnusedLocalInspectionSkipper>("Pythonid.skipUnusedLocal")
  }
}