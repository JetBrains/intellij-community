// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.analysis

import org.jetbrains.uast.UCallExpression

@Suppress("MemberVisibilityCanBePrivate")
object KotlinExtensionConstants {
  const val STANDARD_CLASS: String = "kotlin.StandardKt__StandardKt"
  const val LET_METHOD: String = "let"
  const val ALSO_METHOD: String = "also"
  const val RUN_METHOD: String = "run"
  const val APPLY_METHOD: String = "apply"

  const val LAMBDA_THIS_PARAMETER_NAME: String = "<this>"

  fun isExtensionWithSideEffect(call: UCallExpression): Boolean =
    call.methodName == ALSO_METHOD || call.methodName == APPLY_METHOD

  fun isLetOrRunCall(call: UCallExpression): Boolean =
    call
      .takeIf { it.methodName == LET_METHOD || it.methodName == RUN_METHOD }
      ?.resolve()
      ?.containingClass?.qualifiedName == STANDARD_CLASS

  fun isAlsoOrApplyCall(call: UCallExpression): Boolean =
    call
      .takeIf { it.methodName == ALSO_METHOD || it.methodName == APPLY_METHOD }
      ?.resolve()
      ?.containingClass?.qualifiedName == STANDARD_CLASS

  fun isExtensionFunctionToIgnore(call: UCallExpression): Boolean =
    call
      .takeIf { it.methodName == LET_METHOD || it.methodName == ALSO_METHOD || it.methodName == RUN_METHOD || it.methodName == APPLY_METHOD }
      ?.resolve()
      ?.containingClass?.qualifiedName == STANDARD_CLASS
}