// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.extensions

import com.jetbrains.python.FunctionParameter
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.StringLiteralExpression


/**
 * If literal is argument of some function call -- return callee
 */
fun StringLiteralExpression.getCalleeIfArgument(argumentPosition: Int, argumentName: String? = null): PyExpression? {
  val parent = this.parent ?: return null

  val argumentList = when (parent) {
                       is PyArgumentList -> parent
                       is PyKeywordArgument -> parent.parent as? PyArgumentList
                       else -> null
                     } ?: return null

  if ((this == argumentList.arguments.getOrNull(argumentPosition))
      ||
      (this == argumentName?.run { argumentList.getKeywordArgument(this@run)?.valueExpression })) {
    return argumentList.callExpression?.callee
  }
  return null
}

/**
 * @see getCalleeIfArgument
 */
fun StringLiteralExpression.getCalleeIfArgument(argument: FunctionParameter) = this.getCalleeIfArgument(argument.position, argument.name)