/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.extensions.python

import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction

/**
 * Place right after "def" in class method. Useful to provide custom completions
 */
fun PsiElementPattern.Capture<*>.afterDefInMethod() =
  withLanguage(PythonLanguage.getInstance())
    .and(psiElement().inside(psiElement(PyFunction::class.java).inside(psiElement(PyClass::class.java))))
                               .and(psiElement().afterLeaf("def"))
