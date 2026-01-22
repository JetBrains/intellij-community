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
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.ast.findChildByClass
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyElementImpl
import com.jetbrains.python.psi.types.*

class PyFunctionTypeRepresentation(astNode: ASTNode) : PyElementImpl(astNode), PyExpression {
  val functionName: QualifiedName? by lazy {
    // Function name is the first PyExpression child that comes before the type parameter list (if present)
    // or before the parameter list (if no type parameters)
    val stopAt = typeParameterList ?: parameterList
    for (child in children) {
      if (child === stopAt) break
      if (child is PyExpression) {
        return@lazy QualifiedName.fromDottedString(child.text)
      }
    }
    return@lazy null
  }

  val typeParameterList: PyTypeParameterList?
    get() = findChildByClass(PyTypeParameterList::class.java)

  val parameterList: PyParameterListRepresentation
    get() = findNotNullChildByClass(PyParameterListRepresentation::class.java)

  val returnType: PyExpression?
    get() {
      // Return type is after the -> token
      val arrow = node.findChildByType(PyTokenTypes.RARROW) ?: return null
      var sibling = arrow.treeNext
      while (sibling != null) {
        if (sibling.psi is PyExpression) {
          return sibling.psi as PyExpression
        }
        sibling = sibling.treeNext
      }
      return null
    }

  override fun getType(context: TypeEvalContext, key: TypeEvalContext.Key): PyType? {
    val returnTypeExpr = returnType ?: return null

    // Create type variables from type parameter list
    val typeVarMap = createTypeVarMap(context)

    // Parse callable parameters from the signature (shared by both callable and function types)
    val callableParams = parseCallableParameters(context, typeVarMap)
    val retType = resolveTypeExpression(returnTypeExpr, context, typeVarMap)
    // If we have a function name, this is a 'def' type - try to resolve to PyFunctionType
    val qualifiedFunctionName = functionName
    if (qualifiedFunctionName != null) {
      val resolvedFunction = tryResolveFunction(qualifiedFunctionName, context)

      // If we resolved the function, create PyFunctionType with the signature from the representation
      if (resolvedFunction != null) {
        // Create a custom PyFunctionType that uses our return type, not the function's definition
        return object : PyFunctionTypeImpl(resolvedFunction, callableParams) {
          override fun getReturnType(context: TypeEvalContext): PyType? = retType
        }
      }
      // Fall through to create PyCallableType if resolution failed
    }

    // Create PyCallableType from the signature (for both unresolved functions and plain callables)
    return PyCallableTypeImpl(callableParams, retType)
  }

  private fun createTypeVarMap(context: TypeEvalContext): Map<String, PyTypeVarType> {
    val typeParams = typeParameterList ?: return emptyMap()
    val result = mutableMapOf<String, PyTypeVarType>()

    for (param in typeParams.typeParameters) {
      val paramName = param.name ?: continue

      // Determine bound type from the type parameter's bound expression
      val boundType = param.boundExpression?.let { resolveTypeExpression(it, context, emptyMap()) }

      // Create type variable - PyTypeVarTypeImpl(name, constraints, bound, defaultType, variance)
      val typeVar = PyTypeVarTypeImpl(
        paramName,
        emptyList(), // constraints
        boundType, // bound
        null, // defaultType (Ref<PyType>?)
        PyTypeVarType.Variance.INVARIANT // variance
      )

      result[paramName] = typeVar
    }

    return result
  }

  private fun parseCallableParameters(context: TypeEvalContext, typeVarMap: Map<String, PyTypeVarType>): List<PyCallableParameter> {
    return parameterList.parameters.map { param ->
      when (param) {
        is PySlashParameter -> PyCallableParameterImpl.psi(param)
        is PyNamedParameterTypeRepresentation -> {
          val paramName = param.parameterName
          val paramType = param.typeExpression?.let { resolveTypeExpression(it, context, typeVarMap) }
          PyCallableParameterImpl.nonPsi(paramName, paramType, param.defaultValue)
        }
        is PyStarExpression -> {
          // *args parameter
          // Check if it contains a named parameter
          val namedParam = param.findChildByClass(PyNamedParameterTypeRepresentation::class.java)
          if (namedParam != null) {
            // *args: type
            val paramName = namedParam.parameterName
            val paramType = namedParam.typeExpression?.let { resolveTypeExpression(it, context, typeVarMap) }
            PyCallableParameterImpl.positionalNonPsi(paramName, paramType)
          }
          else {
            // Unnamed *args: *type
            val innerExpr = param.expression
            val paramType = innerExpr?.let { resolveTypeExpression(it, context, typeVarMap) }
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
                resolveTypeExpression(it.expression!!, context, typeVarMap)
              else {
                // Named kwargs: `**name: type`, adapt to `dict`
                val builtins = PyBuiltinCache.getInstance(it)
                PyCollectionTypeImpl(
                  builtins.dictType!!.pyClass, false, listOf(builtins.strType, resolveTypeExpression(it, context, typeVarMap))
                )
              }
            }
            PyCallableParameterImpl.keywordNonPsi(paramName, paramType)
          }
          else {
            // Unnamed kwargs: `**type`
            val innerExpr = param.expression
            val paramType = innerExpr?.let { resolveTypeExpression(it, context, typeVarMap) }
            PyCallableParameterImpl.keywordNonPsi(null, paramType)
          }
        }
        is PyExpression -> {
          val paramType = resolveTypeExpression(param, context, typeVarMap)
          PyCallableParameterImpl.nonPsi(paramType)
        }
        else -> PyCallableParameterImpl.nonPsi(null)
      }
    }
  }

  private fun tryResolveFunction(qualifiedFunctionName: QualifiedName, context: TypeEvalContext): PyFunction? {
    val facade = PyPsiFacade.getInstance(project)
    val resolveContext = facade.createResolveContextFromFoothold(this)

    // Try to resolve the module first (e.g., "test" from "test.A.f")
    val moduleName = qualifiedFunctionName.firstComponent?.let { QualifiedName.fromComponents(it) } ?: return null
    val module = facade.resolveQualifiedName(moduleName, resolveContext).firstOrNull() as? PyFile ?: return null

    // Walk through the remaining components to resolve nested members
    var current: PsiElement? = module
    for (i in 1 until qualifiedFunctionName.componentCount) {
      val componentName = qualifiedFunctionName.components[i] ?: return null

      current = when (val elem = current) {
        is PyFile -> elem.multiResolveName(componentName).firstOrNull()?.element
        is PyClass -> {
          elem.findNestedClass(componentName, false)
          ?: elem.findMethodByName(componentName, false, context)
          ?: elem.findClassAttribute(componentName, false, context)
        }
        else -> null
      }

      if (current == null) return null
    }

    return current as? PyFunction
  }

  private fun resolveTypeExpression(expr: PyExpression, context: TypeEvalContext, typeVarMap: Map<String, PyTypeVarType>): PyType? {
    // Check if this is a reference to a type parameter
    if (expr is PyReferenceExpression && expr.qualifier == null) {
      val name = expr.name
      if (name != null) {
        val typeVar = typeVarMap[name]
        if (typeVar != null) {
          return typeVar
        }
      }
    }

    // Otherwise, resolve normally
    return when (expr) {
      is PyDoubleStarExpression -> PyTypingTypeProvider.getType(expr.expression!!, context)?.get()
      else -> PyTypingTypeProvider.getType(expr, context)?.get()
    }
  }
}
