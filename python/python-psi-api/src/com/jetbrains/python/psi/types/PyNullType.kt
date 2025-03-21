// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult

object PyNullType : PyType {
  override fun resolveMember(name: String, location: PyExpression?, direction: AccessDirection, resolveContext: PyResolveContext): List<RatedResolveResult?>? = null

  override fun getCompletionVariants(completionPrefix: String?, location: PsiElement?, context: ProcessingContext?): Array<out Any?>? = null

  override fun getName(): String = "Null"

  override fun isBuiltin(): Boolean = false

  override fun assertValid(message: String?) {}
}