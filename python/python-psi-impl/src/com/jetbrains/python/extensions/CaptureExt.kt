// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.extensions

import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyParameterList

/**
 * Place right after "def" in class method. Useful to provide custom completions
 */
fun PsiElementPattern.Capture<*>.afterDefInMethod(): PsiElementPattern.Capture<out PsiElement> =
  withLanguage(PythonLanguage.getInstance())
    .and(psiElement().inside(psiElement(PyFunction::class.java).inside(psiElement(PyClass::class.java))))
    .and(psiElement().afterLeaf("def"))

/**
 * Place right after "def" in function or method. Useful to provide custom completions
 */
fun PsiElementPattern.Capture<*>.afterDefInFunction(): PsiElementPattern.Capture<out PsiElement> =
  withLanguage(PythonLanguage.getInstance())
    .and(psiElement().inside(PyFunction::class.java))
    .and(psiElement().afterLeaf("def"))


fun PsiElementPattern.Capture<*>.inParameterList(): PsiElementPattern.Capture<out PsiElement> =
  withLanguage(PythonLanguage.getInstance()).and(psiElement().inside(PyParameterList::class.java))

fun PsiElementPattern.Capture<*>.inArgumentList(): PsiElementPattern.Capture<out PsiElement> =
  withLanguage(PythonLanguage.getInstance()).and(psiElement().inside(PyArgumentList::class.java))
