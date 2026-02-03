/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.uast

/**
 * A common interface as a last resort to represent unhandled [PsiElement] or [KtExpression].
 */
interface UUnknownExpression : UExpression {
  override fun asLogString(): String =
    "[!] " + this::class.java.simpleName + " ($sourcePsi)"
}
