// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion

/**
 * This data class stores information about a possible python object.
 * @param psiName - name of PsiElement, that could be a python object
 * LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance()).isIdentifier()
 * @param pyQualifiedExpressionList - represents a qualified expression in the list
 * @param requiredTypes - list of the required objects type for additional check
 * @see PyQualifiedExpressionItem
 *
 * An example "a.b.c.":
 *     psiName = PyQualifiedExpressionItem("a",PyTokenTypes.DOT),
 *     pyQualifiedExpressionList = [PyQualifiedExpressionItem("b",PyTokenTypes.DOT),PyQualifiedExpressionItem("c",PyTokenTypes.DOT)]
 */
data class PyObjectCandidate(
  val psiName: PyQualifiedExpressionItem,
  val pyQualifiedExpressionList: List<PyQualifiedExpressionItem>,
  val requiredTypes: List<String>? = null,
)