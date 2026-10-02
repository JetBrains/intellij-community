// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.extensions

import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyParameterList
import org.jetbrains.annotations.ApiStatus

/**
 * Place right after "def" in class method. Useful to provide custom completions
 */
@ApiStatus.Internal
fun PsiElementPattern.Capture<*>.afterDefInMethod(): PsiElementPattern.Capture<out PsiElement> =
  withLanguage(PythonLanguage.getInstance())
    .and(psiElement().inside(psiElement(PyFunction::class.java).inside(psiElement(PyClass::class.java))))
    .and(psiElement().afterLeaf("def"))

/**
 * Place right after "def" in function or method. Useful to provide custom completions
 */
@ApiStatus.Internal
fun PsiElementPattern.Capture<*>.afterDefInFunction(): PsiElementPattern.Capture<out PsiElement> =
  withLanguage(PythonLanguage.getInstance())
    .and(psiElement().inside(PyFunction::class.java))
    .and(psiElement().afterLeaf("def"))


@ApiStatus.Internal
fun PsiElementPattern.Capture<*>.inParameterList(): PsiElementPattern.Capture<out PsiElement> =
  withLanguage(PythonLanguage.getInstance()).and(psiElement().inside(PyParameterList::class.java))

@ApiStatus.Internal
fun PsiElementPattern.Capture<*>.inArgumentList(): PsiElementPattern.Capture<out PsiElement> =
  withLanguage(PythonLanguage.getInstance()).and(psiElement().inside(PyArgumentList::class.java))
