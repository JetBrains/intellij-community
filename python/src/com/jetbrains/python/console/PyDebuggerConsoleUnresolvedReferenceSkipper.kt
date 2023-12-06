// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.psi.PsiReference
import com.jetbrains.python.console.PythonConsoleView.CONSOLE_KEY
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyExpressionCodeFragment
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * If static code insight is enabled then ignore unresolved reference from Debugger Console
 */
class PyDebuggerConsoleUnresolvedReferenceSkipper : PyInspectionExtension() {
  override fun ignoreUnresolvedReference(node: PyElement, reference: PsiReference, context: TypeEvalContext): Boolean {
    return node.containingFile.virtualFile.getUserData(CONSOLE_KEY) == true &&
           context.origin !is PyExpressionCodeFragment
  }
}