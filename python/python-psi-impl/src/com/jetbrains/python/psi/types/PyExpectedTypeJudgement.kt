package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PyNames
import com.jetbrains.python.ast.impl.PyPsiUtilsCore.flattenParens
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyAssignmentExpression
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCallSiteExpression
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyDoubleStarExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeyValueExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyLambdaExpression
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyReturnStatement
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PySetLiteralExpression
import com.jetbrains.python.psi.PySliceItem
import com.jetbrains.python.psi.PyStarArgument
import com.jetbrains.python.psi.PyStarExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyYieldExpression
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.mapArguments
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyTypeChecker.hasGenerics
import com.jetbrains.python.psi.types.PyTypeChecker.substitute
import com.jetbrains.python.psi.types.PyTypeChecker.unifyGenericCall


object PyExpectedTypeJudgement {

  /**
   * Computes the expected type of the given expression from its usage in the AST.
   * The expected type is either the explicitly declared type (i.e., type annotation) that constraints the given expression.
   * Or the expected type is implied by the grammar and language semantics, e.g., for varargs.
   * Note that in some cases the expected type has a circular dependency to itself via type inference, which in turn calls the
   * expected type judgment again. This can happen e.g., for cases related to generic functions.
   *
   * Supported root AST elements:
   * - Argument to a call (positional or keyword)
   * - Argument to indexed access (i.e., subscription expression)
   * - RHS of an assignment
   * - Yield expression value (RHS)
   * - Return statement value
   *
   * When given an AST element that is a child C of a root AST element, this method traverses the parent chain upwards
   * to compute the expected type of the root element. Based on that, it tries to conclude the expected type of C.
   *
   * Returns null iff no type declaration, or `Any` was found.
   *
   * Note: At the moment, this method does not resolve subtype relationships in cases where the AST expectations differ
   * from the actual type found. E.g., consider the example `my_var : MyListOfInts = [1]` and suppose that the expected
   * type of `1` is requested. This implementation does not (yet) resolve `MyListOfInts` to compute its relation to the
   * implied supertype `List`, to then retrieve the type of `List`s type parameter.
   */
  @JvmStatic
  fun getExpectedType(expr: PyExpression, ctx: TypeEvalContext): PyType? {
    // Traverse the AST upwards to find the root expression (either assignment, function call, return statement)
    // Do this recursively to easily map the result type to the original sub-element.
    // Example: x2: str; x1, (x2, x3) = (42, (expr, "spam")) # expr is the requested sub-element, the whole tuple is the root expression
    val parent = expr.parent
    when (parent) {
      is PyStarArgument,
      is PyParenthesizedExpression,
        -> {
        return getExpectedType(parent, ctx)
      }

      is PyAssignmentExpression -> {
        val expectedType = fromWalrus(expr)
        if (expectedType != null) return expectedType
        return getExpectedType(parent, ctx)
      }

      is PySliceItem -> {
        val cache = PyBuiltinCache.getInstance(expr)
        return PyUnionType.union(cache.intType, cache.noneType)
      }

      is PyStarExpression -> {
        if (parent.parent is PyExpression) {
          val typeOfStarParent = getExpectedType(parent.parent as PyExpression, ctx)
          if (PyNames.ITERABLE == typeOfStarParent?.name) {
            return typeOfStarParent
          }
          if (typeOfStarParent is PyCollectionType) {
            // upcast to Iterable
            return createIterableType(expr, typeOfStarParent.iteratedItemType)
          }
        }
        return null
      }

      is PyDoubleStarExpression -> {
        if (parent.parent is PyExpression) {
          val typeOfDoubleStarParent = getExpectedType(parent.parent as PyExpression, ctx)
          if (PyNames.MAPPING == typeOfDoubleStarParent?.name) {
            return typeOfDoubleStarParent
          }
          if (typeOfDoubleStarParent is PyCollectionType) {
            // upcast to Map
            return PyCollectionTypeImpl.createTypeByQName(expr, "typing." + PyNames.MAPPING, false, typeOfDoubleStarParent.elementTypes)
          }
        }
        return null
      }

      is PyKeywordArgument -> {
        if (parent.valueExpression == expr) {
          return getExpectedType(parent, ctx)
        }
        return null
      }

      is PyTupleExpression -> {
        val indexOfExpr = parent.elements.indexOf(expr)
        val typeOfParentTuple = getExpectedType(parent, ctx)
        if (typeOfParentTuple is PyTupleType && typeOfParentTuple.elementTypes.isNotEmpty()) {
          return getElementTypeAtTupleIndex(parent, typeOfParentTuple, indexOfExpr)
        }
        if (typeOfParentTuple is PyCollectionType) {
          return typeOfParentTuple.iteratedItemType
        }
        return null
      }

      is PySetLiteralExpression,
      is PyListLiteralExpression,
        -> {
        val typeOfParentList = getExpectedType(parent, ctx)
        if (typeOfParentList is PyCollectionType) {
          return typeOfParentList.iteratedItemType
        }
        return null
      }

      is PyKeyValueExpression -> {
        if (parent.parent is PyDictLiteralExpression) {
          val parentDict = parent.parent as PyDictLiteralExpression
          val typeOfParentDict = getExpectedType(parentDict, ctx)
          if (typeOfParentDict is PyCollectionType && typeOfParentDict.elementTypes.size == 2) {
            val index = if (parent.key == expr) 0 else 1
            return typeOfParentDict.elementTypes[index]
          }
          if (typeOfParentDict is PyTypedDictType && parent.key is PyStringLiteralExpression) {
            val argName = (parent.key as PyStringLiteralExpression).stringValue
            return typeOfParentDict.getElementType(argName)
          }
        }
        return null
      }

      is PyParameterList -> {
        if (expr.parent.parent is PyLambdaExpression && expr is PyParameter) {
          val indexOfExpr = parent.parameters.indexOf(expr)
          val typeOfParentLambda = getExpectedType(parent.parent as PyExpression, ctx)
          if (typeOfParentLambda is PyCallableType) {
            val parameters = typeOfParentLambda.getParameters(ctx)
            if (parameters != null && indexOfExpr >= 0 && indexOfExpr < parameters.size) {
              return parameters[indexOfExpr].getType(ctx)
            }
          }
        }
        return null
      }

      is PyLambdaExpression -> {
        val typeOfParentLambda = getExpectedType(parent, ctx)
        if (typeOfParentLambda is PyCallableType) {
          return typeOfParentLambda.getReturnType(ctx)
        }
        return null
      }
    }

    // Compute the expected type from a given root statement/expression
    return fromArgument(expr, ctx)
           ?: fromAssignment(expr)
           ?: fromYield(expr, ctx)
           ?: fromReturn(expr, ctx)
  }

  private fun fromArgument(callArgument: PyExpression, ctx: TypeEvalContext): PyType? {
    val callSite = (callArgument.parent as? PyArgumentList)?.parent as? PyCallExpression
                   ?: callArgument.parent as? PySubscriptionExpression
                   ?: return null

    val argMappings = callSite.mapArguments(PyResolveContext.defaultContext(ctx))
    val argTypes = LinkedHashSet<PyType?>()

    for (mapping in argMappings) {
      val mappedParameters = mapping.mappedParameters

      val paramType: PyType?
      if (callArgument is PyStarArgument && callSite is PyCallExpression) {
        paramType = fromStarArgument(callArgument, mapping, ctx)
      }
      else {
        val param = mappedParameters[callArgument]
                    ?: return null // This would be a union with Any, hence return null here already

        val paramTypeOrUnpacked = param.getArgumentType(ctx)
        if (paramTypeOrUnpacked is PyUnpackedTupleType) {
          // happens here: f(1, "s") for function: def f(*args: *tuple[int,str]): pass;
          if (paramTypeOrUnpacked.isUnbound) {
            paramType = paramTypeOrUnpacked.elementTypes.firstOrNull()
          }
          else {
            val paramIdx = mappedParameters.keys.indexOf(callArgument)
            paramType = paramTypeOrUnpacked.elementTypes.getOrElse(paramIdx) { null }
          }
        }
        else {
          paramType = paramTypeOrUnpacked
        }
      }
      argTypes.add(substituteTypeVars(paramType, callSite, mappedParameters, ctx))
    }

    return PyUnionType.union(argTypes)
  }

  private fun fromStarArgument(callArgument: PyStarArgument, mapping: PyCallExpression.PyArgumentsMapping, ctx: TypeEvalContext): PyType? {
    val mappedParameters = mapping.mappedParameters
    if (callArgument.isKeyword) {
      val param = mappedParameters.values.firstOrNull { cp -> cp.isKeywordContainer }
      if (param == null) {
        // The function declares no kwargs, but the caller passed a starred expression:
        // E.g.: def f(s: str, n: int) gets called f(**{"s": "foo", "n": 123}).

        val dictClass = PyBuiltinCache.getInstance(callArgument).getClass("dict") ?: return null
        val fields = mutableMapOf<String, PyTypedDictType.FieldTypeAndTotality>()
        for (parameter in mapping.parametersMappedToVariadicKeywordArguments) {
          val name = parameter.name
          if (name == null || parameter.isSelf || parameter.isPositionalContainer || parameter.isKeywordContainer) {
            continue
          }
          fields[name] = PyTypedDictType.FieldTypeAndTotality(
            value = null, // We define a schema, not a specific instance value
            type = parameter.getType(ctx),
            qualifiers = PyTypedDictType.TypedDictFieldQualifiers(isRequired = !parameter.hasDefaultValue())
          )
        }

        return PyTypedDictType(
          name = "Parameters",
          fields = fields,
          dictClass = dictClass,
          definitionLevel = PyTypedDictType.DefinitionLevel.INSTANCE,
          ancestors = emptyList(),
          declaration = mapping.callableType?.declarationElement
        )
      }
      // TODO merge the type of `**kwargs` with the types of other mapped parameters here
      else {
        return param.getType(ctx)
      }
    }
    else {
      val param = mappedParameters.values.firstOrNull { cp -> cp.isPositionalContainer }
      if (param == null) {
        // The function declares no varargs, but the caller passed a starred expression:
        // E.g.: def f(s: str, n: int) gets called f(*("foo", 123)).

        val paramTypes = mapping.parametersMappedToVariadicPositionalArguments.map { cp -> cp.getType(ctx) }
        return PyTupleType.create(callArgument, paramTypes)
      }
      else {
        return param.getType(ctx)
      }
    }
  }

  private fun substituteTypeVars(
    paramType: PyType?,
    callSite: PyCallSiteExpression,
    mappedParameters: Map<PyExpression, PyCallableParameter>,
    ctx: TypeEvalContext,
  ): PyType? {
    if (!hasGenerics(paramType, ctx)) return paramType

    val receiver = callSite.getReceiver(null)
    val substitutions = unifyGenericCall(receiver, mappedParameters, ctx) // might cause recursion
    if (substitutions == null) return paramType

    return substitute(paramType, substitutions, ctx)
  }

  private fun fromWalrus(expr: PyExpression): PyType? {
    val parent = expr.parent as? PyAssignmentExpression ?: return null
    if (parent.assignedValue != expr) return null
    val lhs = parent.target ?: return null
    val rhs = parent.assignedValue ?: return null
    val avoidControlFlowCtx = TypeEvalContext.codeInsightFallback(null)
    return fromLhs(lhs, rhs, avoidControlFlowCtx)
  }

  private fun fromAssignment(expr: PyExpression): PyType? {
    val parent = expr.parent as? PyAssignmentStatement ?: return null
    if (parent.assignedValue != expr) return null
    val lhs = parent.leftHandSideExpression ?: return null
    val rhs = parent.assignedValue ?: return null
    val avoidControlFlowCtx = TypeEvalContext.codeInsightFallback(null)
    return fromLhs(lhs, rhs, avoidControlFlowCtx)
  }

  private fun fromLhs(lhs: PyExpression, rhs: PyExpression?, ctx: TypeEvalContext): PyType? {
    if (lhs is PyParenthesizedExpression || rhs is PyParenthesizedExpression) {
      // unwrap parentheses
      val lhsUnparenthesized = flattenParens(lhs) as? PyExpression ?: return null
      val rhsUnparenthesized = flattenParens(rhs) as? PyExpression ?: return null
      return fromLhs(lhsUnparenthesized, rhsUnparenthesized, ctx)
    }

    when (lhs) {
      is PySequenceExpression -> {
        // try to mutually descent the nested sequences both on lhs and on rhs
        val tupleElementTypes = ArrayList<PyType?>()
        for (idx in 0 until lhs.elements.size) {
          val lhsElem = lhs.elements[idx]
          val rhsElem = if (rhs is PySequenceExpression) rhs.elements[idx] else null
          val elemType = fromLhs(lhsElem, rhsElem, ctx)
          tupleElementTypes.add(elemType)
        }
        if (rhs is PyTupleExpression) {
          // On the RHS of the assignment we are inside a tuple, hence it is safe to downcast the current type to tuple.
          // The benefit is that we can preserve the positional element type information.
          return PyTupleType.create(lhs, tupleElementTypes)
        }
        else {
          val iterableElementTypes = ArrayList<PyType?>()
          for (tupleElementType in tupleElementTypes) {
            if (tupleElementType is PyUnpackedTupleType) {
              iterableElementTypes.addAll(tupleElementType.elementTypes) // simplify
            }
            else {
              iterableElementTypes.add(tupleElementType)
            }
          }
          if (iterableElementTypes.contains(null)) {
            return createIterableType(lhs, null) // simplify
          }
          val iterableElementTypesUnion = PyUnionType.union(iterableElementTypes)
          return createIterableType(lhs, iterableElementTypesUnion)
        }
      }

      is PyStarExpression -> {
        val starChild = lhs.expression
        val starChildType = if (starChild == null) null else fromLhs(starChild, rhs, ctx)
        if (starChildType is PyTupleType) {
          if (starChildType.isHomogeneous) {
            return PyUnpackedTupleTypeImpl.createUnbound(starChildType.iteratedItemType)
          }
          else {
            return PyUnpackedTupleTypeImpl.create(starChildType.elementTypes)
          }
        }
        return starChildType
      }

      is PySubscriptionExpression -> {
        val operandType = ctx.getType(lhs.operand)
        val iterableType = if (operandType is PyCollectionType) operandType.iteratedItemType else null
        if (lhs.indexExpression is PySliceItem) {
          return createIterableType(lhs, iterableType)
        }
        return iterableType
      }

      is PyTargetExpression -> {
        // the following code is supposed to only consider explicitly declared type annotations
        val resolvedReference = lhs.reference.resolve()

        if (resolvedReference is PyNamedParameter) {
          val parameterList = resolvedReference.parent
          val indexOfExpr = (parameterList as? PyParameterList)?.parameters?.indexOf(resolvedReference) ?: -1
          val parameterListHolder = parameterList?.parent
          val callableType = when (parameterListHolder) {
            is PyFunction -> ctx.getType(parameterListHolder)
            is PyLambdaExpression -> getExpectedType(parameterListHolder, ctx)
            else -> null
          }

          if (callableType is PyCallableType) {
            val parameters = callableType.getParameters(ctx)
            if (parameters != null && indexOfExpr >= 0 && indexOfExpr < parameters.size) {
              return parameters[indexOfExpr].getType(ctx)
            }
          }
          return null
        }
        if (resolvedReference is PyTypedElement) {
          val pyType = PyTypingTypeProvider().getReferenceType(resolvedReference, ctx, null)
          if (pyType != null) {
            return pyType.get()
          }
        }
        val pyType = PyTypingTypeProvider().getReferenceType(lhs, ctx, null)
        if (pyType != null) {
          return pyType.get()
        }
        // TODO: maybe support types from Doc-Strings using: (expr as PyTargetExpressionImpl).getTypeFromDocString()
        return null
      }
    }

    return null
  }

  private fun fromYield(expr: PyExpression, ctx: TypeEvalContext): PyType? {
    val parent = expr.parent as? PyYieldExpression ?: return null
    val funScope = parent.parentOfType<PyFunction>() ?: return null

    val returnType = ctx.getReturnType(funScope)
    val generatorDescriptor = PyTypingTypeProvider.GeneratorTypeDescriptor.fromGenerator(returnType)
    val yieldType = generatorDescriptor?.yieldType()
    if (parent.isDelegating) {
      return createIterableType(expr, yieldType)
    }
    return yieldType
  }

  private fun fromReturn(expr: PyExpression, ctx: TypeEvalContext): PyType? {
    val parent = expr.parent as? PyReturnStatement ?: return null
    val funScope = parent.parentOfType<PyFunction>() ?: return null
    if (funScope.annotation == null) return null // no explicit return type annotation, hence any return value is acceptable

    val returnType = ctx.getReturnType(funScope)
    if (funScope.isAsync) {
      return PyTypingTypeProvider.unwrapCoroutineReturnType(returnType)?.get()
    }
    val generatorReturnType = PyTypingTypeProvider.GeneratorTypeDescriptor.fromGenerator(returnType)?.returnType()
    return generatorReturnType ?: returnType
  }

  /**
   * Returns the PyType of a tuple expression at the given index.
   *
   * By design this method computes the complete array of the tuple elements.
   * The reason is that this makes it much easier to spot bugs while having only minimal impact on memory/time performance.
   */
  private fun getElementTypeAtTupleIndex(tupleExpr: PyTupleExpression, tupleType: PyTupleType, indexOfExpr: Int): PyType? {
    if (indexOfExpr < 0) return null

    val tupleTypeArray = arrayOfNulls<PyType?>(tupleExpr.elements.size)
    val elementTypes = tupleType.elementTypes
    val variadicRepeatCount = tupleExpr.elements.size - elementTypes.size + 1
    var arrayIdx = 0
    for (idx in 0 until tupleType.elementTypes.size) {
      val elemType = tupleType.elementTypes[idx]
      if (elemType is PyUnpackedTupleType && elemType.isUnbound) {
        repeat(variadicRepeatCount) {
          tupleTypeArray[arrayIdx++] = elemType.elementTypes.firstOrNull()
        }
        continue
      }
      if (elemType is PyTupleType && elemType.isHomogeneous) {
        repeat(variadicRepeatCount) {
          tupleTypeArray[arrayIdx++] = elemType.elementTypes.firstOrNull()
        }
        continue
      }
      tupleTypeArray[arrayIdx++] = elemType
    }
    if (indexOfExpr < tupleTypeArray.size) {
      return tupleTypeArray[indexOfExpr]
    }
    return null
  }

  private fun createIterableType(anchor: PsiElement, elementType: PyType?): PyCollectionTypeImpl? {
    return PyCollectionTypeImpl.createTypeByQName(anchor, "typing." + PyNames.ITERABLE, false, listOf(elementType))
  }
}
