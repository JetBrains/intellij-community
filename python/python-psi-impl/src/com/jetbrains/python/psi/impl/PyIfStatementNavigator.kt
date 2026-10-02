// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyIfStatementNavigator")

package com.jetbrains.python.psi.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyIfStatement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun getIfStatementByIfKeyword(ifKeyword: PsiElement?): PyIfStatement? {
  val statement = PsiTreeUtil.getParentOfType(ifKeyword, PyIfStatement::class.java) ?: return null
  return if (statement.ifPart.firstChild === ifKeyword) statement else null
}

