// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.jetbrains.python.ast.PyAstDecoratable
import com.jetbrains.python.ast.PyAstStringLiteralExpression
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.resolve.PyResolveUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal val deprecationDecorators = arrayOf("typing_extensions.deprecated", "warnings.deprecated")

@ApiStatus.Internal
fun extractDeprecationMessageFromDecorator(element: PyAstDecoratable): String? {
  val deprecatedDecorator = element.decoratorList?.decorators?.firstOrNull { it.name == "deprecated" } ?: return null
  if (deprecatedDecorator.arguments.isEmpty()) {
    return null
  }
  val decoratorCall = deprecatedDecorator.callee as? PyReferenceExpression ?: return null
  if (decoratorCall.asQualifiedName()?.toString() !in deprecationDecorators) {
    if (!PyResolveUtil.resolveLocally(
        decoratorCall).mapNotNull { it.parent as? PyFromImportStatement }.flatMap { it.fullyQualifiedObjectNames }.any { it in deprecationDecorators }
    ) {
      return null
    }
  }

  return (deprecatedDecorator.arguments[0] as? PyAstStringLiteralExpression)?.stringValue
}