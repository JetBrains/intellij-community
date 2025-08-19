/*
 * Copyright 2000-2025 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.typeRepresentation.psi

import com.intellij.lang.ASTNode
import com.jetbrains.python.ast.findChildByClass
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyDoubleStarExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PySlashParameter
import com.jetbrains.python.psi.PyStarExpression
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyElementImpl
import com.jetbrains.python.psi.types.*

class PyFunctionTypeRepresentation(astNode: ASTNode) : PyElementImpl(astNode), PyExpression {
  val parameterList: PyParameterListRepresentation
    get() = findNotNullChildByClass(PyParameterListRepresentation::class.java)

  val returnType: PyExpression?
    get() = findChildByClass(PyExpression::class.java)

  override fun getType(context: TypeEvalContext, key: TypeEvalContext.Key): PyType? {
    val returnType = returnType ?: return null
    val params = parameterList.parameters
    val callableParams = params.map { param ->
      when (param) {
        is PySlashParameter -> {
          // Positional-only separator
          PyCallableParameterImpl.psi(param)
        }
        is PyNamedParameterTypeRepresentation -> {
          val paramName = param.parameterName
          val paramType = param.typeExpression?.let { resolveTypeExpression(it, context) }
          PyCallableParameterImpl.nonPsi(paramName, paramType, param.defaultValue)
        }
        is PyStarExpression -> {
          // *args parameter
          // Check if it contains a named parameter
          val namedParam = param.findChildByClass(PyNamedParameterTypeRepresentation::class.java)
          if (namedParam != null) {
            // *args: type
            val paramName = namedParam.parameterName
            val paramType = namedParam.typeExpression?.let { resolveTypeExpression(it, context) }
            PyCallableParameterImpl.positionalNonPsi(paramName, paramType)
          }
          else {
            // Unnamed *args: *type
            val innerExpr = param.expression
            val paramType = innerExpr?.let { resolveTypeExpression(it, context) }
            PyCallableParameterImpl.positionalNonPsi(null, paramType)
          }
        }
        is PyDoubleStarExpression -> {
          // **kwargs parameter
          // Check if it contains a named parameter
          val namedParam = param.findChildByClass(PyNamedParameterTypeRepresentation::class.java)
          if (namedParam != null) {
            val paramName = namedParam.parameterName
            val paramType = namedParam.typeExpression?.let {
              if (it is PyDoubleStarExpression)
              // Named kwargs unpacked: `**name: **type`
                resolveTypeExpression(it.expression!!, context)
              else {
                // Named kwargs: `**name: type`, adapt to `dict`
                val builtins = PyBuiltinCache.getInstance(it)
                PyCollectionTypeImpl(
                  builtins.dictType!!.pyClass, false, listOf(builtins.strType, resolveTypeExpression(it, context))
                )
              }
            }
            PyCallableParameterImpl.keywordNonPsi(paramName, paramType)
          }
          else {
            // Unnamed kwargs: `**type`
            val innerExpr = param.expression
            val paramType = innerExpr?.let { resolveTypeExpression(it, context) }
            PyCallableParameterImpl.keywordNonPsi(null, paramType)
          }
        }
        is PyExpression -> {
          val paramType = resolveTypeExpression(param, context)
          PyCallableParameterImpl.nonPsi(paramType)
        }
        else -> {
          PyCallableParameterImpl.nonPsi(null)
        }
      }
    }
    val retType = resolveTypeExpression(returnType, context)
    return PyCallableTypeImpl(callableParams, retType)
  }

  private fun resolveTypeExpression(expr: PyExpression, context: TypeEvalContext): PyType? = when (expr) {
    is PyDoubleStarExpression -> PyTypingTypeProvider.getType(expr.expression!!, context)?.get()
    else -> PyTypingTypeProvider.getType(expr, context)?.get()
  }
}
