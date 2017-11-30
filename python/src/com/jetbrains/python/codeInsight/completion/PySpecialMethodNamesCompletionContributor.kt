// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.openapi.util.Pair
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.types.TypeEvalContext

class PySpecialMethodNamesCompletionContributor : PyMethodNamesCompletionContributor() {
  override fun getCompletions(aClass: PyClass, context: TypeEvalContext) =
    PyNames.getBuiltinMethods(LanguageLevel.forElement(aClass)).map { Pair(it.key, it.value.signature) }
}