// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider

class PyLenPostfixTemplate(provider: PostfixTemplateProvider) :
  PyEditablePostfixTemplate(
    "len",
    "len",
    "len(\$EXPR$)\$END$",
    DESCR,
    setOf(PyPostfixTemplateExpressionCondition.PyBuiltinLenApplicable()),
    false,
    provider,
    true
  ) {

  companion object {
    const val DESCR = "len(expr)"
  }
}
