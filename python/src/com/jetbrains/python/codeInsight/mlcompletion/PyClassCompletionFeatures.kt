// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.completion.ml.CompletionEnvironment
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyUtil

object PyClassCompletionFeatures {
  data class ClassFeatures(val diffLinesWithClassDef: Int, val classHaveConstructor: Boolean)

  fun getClassCompletionFeatures(environment: CompletionEnvironment): ClassFeatures? {
    val parentClass = PsiTreeUtil.getParentOfType(environment.parameters.position, PyClass::class.java) ?: return null

    val lookup = environment.lookup
    val editor = lookup.topLevelEditor
    val caretOffset = lookup.lookupStart
    val logicalPosition = editor.offsetToLogicalPosition(caretOffset)
    val lineno = logicalPosition.line

    val classLogicalPosition = editor.offsetToLogicalPosition(parentClass.textOffset)
    val classLineno = classLogicalPosition.line
    val classHaveConstructor = parentClass.methods.any { PyUtil.isInitOrNewMethod(it) }

    return ClassFeatures(lineno - classLineno, classHaveConstructor)
  }
}