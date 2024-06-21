package com.jetbrains.python.ast.impl

import com.jetbrains.python.ast.PyAstDecoratable
import com.jetbrains.python.ast.PyAstStringLiteralExpression

val deprecatedDecoratorContainers = arrayOf("typing_extensions.pyi", "warnings.pyi")

fun extractDeprecationMessageFromDecorator(element: PyAstDecoratable): String? {
  val deprecatedDecorator = element.decoratorList?.decorators?.firstOrNull { it.name == "deprecated" } ?: return null
  val annotationClass = deprecatedDecorator.callee?.reference?.resolve() ?: return null
  if (annotationClass.containingFile?.name !in deprecatedDecoratorContainers) {
    return null
  }

  val argumentList = deprecatedDecorator.argumentList ?: return null

  if (argumentList.arguments.isEmpty()) {
    return null
  }
  val argument = argumentList.arguments[0] as? PyAstStringLiteralExpression ?: return null
  return argument.stringValue
}