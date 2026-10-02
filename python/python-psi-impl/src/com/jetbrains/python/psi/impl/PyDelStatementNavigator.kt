// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyDelStatementNavigator")

package com.jetbrains.python.psi.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyDelStatement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun getDelStatementByTarget(target: PsiElement): PyDelStatement? {
  val statement = PsiTreeUtil.getParentOfType(target, PyDelStatement::class.java) ?: return null
  return if (statement.targets.contains(target)) statement else null
}

