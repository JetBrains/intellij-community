// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PyAssignmentStatementNavigator")

package com.jetbrains.python.psi.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyAssignmentStatement

fun getStatementByTarget(target: PsiElement?): PyAssignmentStatement? {
  val statement = PsiTreeUtil.getParentOfType(target, PyAssignmentStatement::class.java) ?: return null
  return if (statement.targets.contains(target)) statement else null
}
