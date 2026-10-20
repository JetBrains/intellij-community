// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyExpressionCodeFragment
import com.jetbrains.python.psi.PythonVisitorFilter

class PyExpressionCodeFragmentVisitorFilter : PythonVisitorFilter {
  override fun isSupported(visitorClass: Class<out PyElementVisitor>, file: PsiFile): Boolean {
    if (file is PyExpressionCodeFragment) {
      if (visitorClass == PyPep8Inspection::class.java) {
        return false
      }
    }
    return true
  }
}