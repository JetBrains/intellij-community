// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi

import com.jetbrains.python.ast.PyAstSequencePattern
import com.jetbrains.python.ast.findChildrenByClass

interface PySequencePattern : PyAstSequencePattern, PyPattern, PyCaptureContext {
  val elements: List<PyPattern> 
    get() = findChildrenByClass(PyPattern::class.java).toList()
}
