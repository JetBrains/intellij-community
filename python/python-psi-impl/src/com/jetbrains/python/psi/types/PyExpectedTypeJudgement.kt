package com.jetbrains.python.psi.types

import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.StackOverflowPreventedException
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.ast.impl.PyPsiUtilsCore.flattenParens
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyAssignmentExpression
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDictCompExpression
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyDoubleStarExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyGeneratorExpression
import com.jetbrains.python.psi.PyKeyValueExpression
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyLambdaExpression
import com.jetbrains.python.psi.PyListCompExpression
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyReturnStatement
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PySetCompExpression
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
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyTypeChecker.hasGenerics
import com.jetbrains.python.psi.types.PyTypeChecker.substitute


object PyExpectedTypeJudgement {
  private val recursionGuard = RecursionManager.createGuard<Pair<PsiElement, TypeEvalContext>>("PyExpectedTypeJudgement")

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
    try {
      return recursionGuard.computePreventingRecursion<PyType?, Throwable>(Pair(expr.parent, ctx), false) {
        doGetExpectedType(expr, ctx)
      }
    }
    catch (_: StackOverflowPreventedException) {
      return PyAnyType.unknown
    }
  }

  fun doGetExpectedType(expr: PyExpression, ctx: TypeEvalContext): PyType? {
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
        val expectedType = fromWalrus(expr, ctx)
        if (expectedType != null && !expectedType.isUnknown) return expectedType
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
          if (typeOfStarParent is PyClassType && typeOfStarParent.isParameterized) {
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
          if (typeOfDoubleStarParent is PyClassType && typeOfDoubleStarParent.isParameterized) {
            // upcast to Map
            return PyCollectionTypeImpl.createTypeByQName(expr, "typing." + PyNames.MAPPING, false, typeOfDoubleStarParent.typeArguments)
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
        if (typeOfParentTuple is PyClassType && typeOfParentTuple.isParameterized) {
          return typeOfParentTuple.iteratedItemType
        }
        return null
      }

      is PySetLiteralExpression,
      is PyListLiteralExpression,
        -> {
        val typeOfParentList = getExpectedType(parent, ctx)
        if (typeOfParentList is PyClassType && typeOfParentList.isParameterized) {
          return typeOfParentList.iteratedItemType
        }
        return null
      }

      is PyKeyValueExpression -> {
        val grandParent = parent.parent
        val typeOfParentDict = when (grandParent) {
          is PyDictLiteralExpression
            -> getExpectedType(grandParent, ctx)
          is PyDictCompExpression if (grandParent.resultExpression === parent)
            -> getExpectedType(grandParent, ctx)
          else -> return null
        }
        if (typeOfParentDict is PyClassType && typeOfParentDict.typeArguments.size == 2) {
          val index = if (parent.key == expr) 0 else 1
          return typeOfParentDict.typeArguments[index]
        }
        if (typeOfParentDict is PyUnpackedTypedDictType && parent.key is PyStringLiteralExpression) {
          val argName = (parent.key as PyStringLiteralExpression).stringValue
          return typeOfParentDict.typedDictType.getElementType(argName)
        }
        return null
      }

      is PyParameterList -> {
        if (expr.parent.parent is PyLambdaExpression && expr is PyParameter) {
          val indexOfExpr = parent.parameters.indexOf(expr)
          var typeOfParentLambda = getExpectedType(parent.parent as PyExpression, ctx)

          if (typeOfParentLambda is PyUnionType) {
            // match first callable
            typeOfParentLambda = typeOfParentLambda.members.firstOrNull { it is PyCallableType && it.isCallable }
          }
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

      is PyListCompExpression,
      is PySetCompExpression,
      is PyGeneratorExpression
        -> {
        if (parent.resultExpression === expr) {
          val expectedComprehensionType = getExpectedType(parent, ctx) ?: return null
          if (expectedComprehensionType is PyClassType && expectedComprehensionType.isParameterized) {
            return expectedComprehensionType.typeArguments.firstOrNull()
          }
        }
      }

      is PyBinaryExpression -> {
        return fromBinaryExpression(expr, parent, ctx)
      }
    }

    // Compute the expected type from a given root statement/expression
    return fromArgument(expr, ctx)
           ?: fromAssignment(expr, ctx)
           ?: fromYield(expr, ctx)
           ?: fromReturn(expr, ctx)
           ?: PyAnyType.unknown
  }

  private fun fromArgument(callArgument: PyExpression, ctx: TypeEvalContext): PyType? {
    val callSite = (callArgument.parent as? PyArgumentList)?.parent as? PyCallExpression
                   ?: callArgument.parent as? PySubscriptionExpression
                   ?: return null

    val argMappings = PyCallExpressionHelper.mapArguments(callSite, PyResolveContext.defaultContext(ctx))
    val argTypes = LinkedHashSet<PyType?>()

    for (mapping in argMappings) {
      val mappedParameters = mapping.mappedParameters

      val paramType: PyType?
      if (callArgument is PyStarArgument && callSite is PyCallExpression) {
        paramType = fromStarArgument(callArgument, mapping, ctx)
      }
      else {
        val param = mappedParameters[callArgument] ?: continue

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

      argTypes.add(substituteTypeVars(paramType, mapping, ctx))
    }

    return PyUnionType.union(argTypes)
  }

  private fun fromStarArgument(callArgument: PyStarArgument, mapping: PyCallExpression.PyArgumentsMapping, ctx: TypeEvalContext): PyType? {
    val mappedParameters = mapping.mappedParameters
    if (callArgument.isKeyword) {
      val kwargsContainer = mappedParameters.values.firstOrNull { cp -> cp.isKeywordContainer }
      val kwargsContainerType = kwargsContainer?.getType(ctx)

      val extraParams = mapping.parametersMappedToVariadicKeywordArguments.filter { parameter ->
        val name = parameter.name
        name != null && !parameter.isSelf && !parameter.isPositionalContainer && !parameter.isKeywordContainer
      }

      if (extraParams.isEmpty() && kwargsContainer != null) {
        return kwargsContainerType
      }

      // TODO For non-variadic types of **kwargs requires supporting extra_items in PyTypedDictType PY-85421
      val baseFields = (kwargsContainerType as? PyUnpackedTypedDictType)?.typedDictType?.fields.orEmpty()
      val fields = mutableMapOf<String, PyTypedDictType.FieldTypeAndTotality>()
      fields.putAll(baseFields)
      for (parameter in extraParams) {
        val name = parameter.name ?: continue
        if (fields.containsKey(name)) continue
        fields[name] = PyTypedDictType.FieldTypeAndTotality(
          value = null, // We define a schema, not a specific instance value
          type = parameter.getType(ctx),
          qualifiers = PyTypedDictType.TypedDictFieldQualifiers(isRequired = !parameter.hasDefaultValue())
        )
      }

      val dictClass = PyBuiltinCache.getInstance(callArgument).getClass("dict") ?: return null
      val typedDictType = PyTypedDictType(
        name = "Parameters",
        fields = fields,
        dictClass = dictClass,
        isDefinition = false,
        // TODO: This is incorrect:
        // `PyTypedDict` declaration is either a `PyClass` node:
        // ```
        // class TD(typing.TypedDict):
        //   ...
        // ```
        //
        // or a `PyTargetExpression` node:
        // ```
        // TD = typing.TypedDict("TD", {})
        // ```
        declaration = mapping.callableType?.callable ?: return null
      )
      return PyUnpackedTypedDictTypeImpl(typedDictType)
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
    mapping: PyCallExpression.PyArgumentsMapping,
    ctx: TypeEvalContext,
  ): PyType? {
    if (!paramType.hasGenerics(ctx)) return paramType
    val substitutions = PyTypeInferenceCspFactory.unifyReceiver(mapping, ctx)
    return substitute(paramType, substitutions, ctx)
  }

  private fun fromWalrus(expr: PyExpression, ctx: TypeEvalContext): PyType? {
    val parent = expr.parent as? PyAssignmentExpression ?: return null
    if (parent.assignedValue != expr) return null
    val lhs = parent.target ?: return null
    val rhs = parent.assignedValue ?: return null
    return fromLhs(lhs, rhs, ctx)
  }

  private fun fromAssignment(expr: PyExpression, ctx: TypeEvalContext): PyType? {
    val parent = expr.parent as? PyAssignmentStatement ?: return null
    if (parent.assignedValue != expr) return null
    val lhs = parent.leftHandSideExpression ?: return null
    val rhs = parent.assignedValue ?: return null
    return fromLhs(lhs, rhs, ctx)
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
        for ((idx, element) in lhs.elements.withIndex()) {
          val lhsElem = element
          val rhsElem = if (rhs is PySequenceExpression) rhs.elements[idx] else null
          val elemType = fromLhs(lhsElem, rhsElem, ctx) ?: PyAnyType.unknown
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
          if (iterableElementTypes.contains(PyAnyType.unknown)) {
            return createIterableType(lhs, PyAnyType.unknown) // simplify
          }
          if (iterableElementTypes.contains(PyAnyType.any)) {
            return createIterableType(lhs, PyAnyType.any) // simplify
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
        val iterableType = if (operandType is PyClassType && operandType.isParameterized) operandType.iteratedItemType else PyAnyType.any
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
    val yieldType = generatorDescriptor?.yieldType ?: PyAnyType.unknown
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
    val generatorReturnType = PyTypingTypeProvider.GeneratorTypeDescriptor.fromGenerator(returnType)?.returnType
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
    for (elemType in elementTypes.take(tupleTypeArray.size)) {
      when (elemType) {
        is PyUnpackedTupleType if (elemType.isUnbound) -> {
          repeat(variadicRepeatCount) {
            tupleTypeArray[arrayIdx++] = elemType.elementTypes.firstOrNull()
          }
        }
        is PyTupleType if (elemType.isHomogeneous) -> {
          repeat(variadicRepeatCount) {
            tupleTypeArray[arrayIdx++] = elemType.elementTypes.firstOrNull()
          }
        }
        else -> {
          tupleTypeArray[arrayIdx++] = elemType
        }
      }
    }
    if (indexOfExpr < tupleTypeArray.size) {
      return tupleTypeArray[indexOfExpr]
    }
    return null
  }

  /**
   * Computes the expected type of the operand [expr] inside the binary expression [binaryExpr]:
   * - **Rich comparison** (`<`, `>`, `<=`, `>=`, `==`, `!=`, `<>`): synthetic dunder protocol (e.g., `__lt__`), unioned with `Any`
   * - **Membership** (`in`, `not in`): `__contains__` protocol for the container operand
   * - **Identity** (`is`, `is not`): no expectation, returns null
   * - **Short-circuit logical** (`and`, `or`): propagates the parent's expected type
   * - **Sequence repetition** (`seq * int` or `int * seq`): `SupportsIndex` (`__index__() -> int`) for the non-sequence operand
   * - **Arithmetic, bitwise, shift, matmul** (`+`, `-`, `*`, `/`, `//`, `%`, `**`, `@`, `<<`, `>>`, `&`, `|`, `^`):
   *   synthetic operator protocol (e.g., `__add__`/`__radd__`), unioned with `Any`
   * - **Fallback**: Otherwise propagate the parent's expected type.
   */
  private fun fromBinaryExpression(expr: PyExpression, binaryExpr: PyBinaryExpression, ctx: TypeEvalContext): PyType? {
    val operator = binaryExpr.operator
    if (operator == null) {
      return null
    }

    val comparisonProtocolType = fromComparisonExpression(expr, binaryExpr, operator, ctx)
    if (comparisonProtocolType != null) {
      return comparisonProtocolType
    }
    if (PyTokenTypes.COMPARISON_OPERATIONS.contains(operator)) {
      return null
    }

    val expectedTypeOfWholeExpr = getExpectedType(binaryExpr, ctx)

    // Short-circuit operators: `a and b`, `a or b` evaluate to one of the operands.
    if (operator == PyTokenTypes.AND_KEYWORD || operator == PyTokenTypes.OR_KEYWORD) {
      return expectedTypeOfWholeExpr
    }

    if (operator == PyTokenTypes.MULT) {
      // Special case for sequence repetition: [1,2] * count.
      val otherOperand: PyExpression? = when {
        expr === binaryExpr.leftExpression -> binaryExpr.rightExpression
        expr === binaryExpr.rightExpression -> binaryExpr.leftExpression
        else -> null
      }
      if (otherOperand != null) {
        val cache = PyBuiltinCache.getInstance(expr)
        val intType = cache.intType
        val typeOfOther = ctx.getType(otherOperand)
        if (isSequenceLike(typeOfOther)) {
          // Create:  `__index__() -> int`, i.e. `SupportsIndex`.
          val syntheticType = createSyntheticDunderProtocolType(expr, "__index__", null, intType, ctx)
          return syntheticType
        }
      }
    }

    val operatorProtocolType = fromOperatorExpression(expr, binaryExpr, operator, ctx)
    if (operatorProtocolType != null) {
      return operatorProtocolType
    }

    // Fallback: propagate the parent's expected type.
    return expectedTypeOfWholeExpr
  }

  /**
   * Binary operator operands are constrained structurally by Python's operator protocol.
   * Since Python may let the other operand compensate via the direct/reflected fallback path,
   * the synthetic expectation is unioned with `Any`.
   */
  private fun fromOperatorExpression(expr: PyExpression, binaryExpr: PyBinaryExpression, operator: IElementType, ctx: TypeEvalContext): PyType? {
    val leftExpr = binaryExpr.leftExpression
    val rightExpr = binaryExpr.rightExpression

    val methodName = when (operator) {
                       PyTokenTypes.PLUS -> if (expr === leftExpr) "__add__" else if (expr === rightExpr) "__radd__" else null
                       PyTokenTypes.MINUS -> if (expr === leftExpr) "__sub__" else if (expr === rightExpr) "__rsub__" else null
                       PyTokenTypes.MULT -> if (expr === leftExpr) "__mul__" else if (expr === rightExpr) "__rmul__" else null
                       PyTokenTypes.AT -> if (expr === leftExpr) "__matmul__" else if (expr === rightExpr) "__rmatmul__" else null
                       PyTokenTypes.DIV -> if (expr === leftExpr) "__truediv__" else if (expr === rightExpr) "__rtruediv__" else null
                       PyTokenTypes.FLOORDIV -> if (expr === leftExpr) "__floordiv__" else if (expr === rightExpr) "__rfloordiv__" else null
                       PyTokenTypes.PERC -> if (expr === leftExpr) "__mod__" else if (expr === rightExpr) "__rmod__" else null
                       PyTokenTypes.EXP -> if (expr === leftExpr) "__pow__" else if (expr === rightExpr) "__rpow__" else null
                       PyTokenTypes.LTLT -> if (expr === leftExpr) "__lshift__" else if (expr === rightExpr) "__rlshift__" else null
                       PyTokenTypes.GTGT -> if (expr === leftExpr) "__rshift__" else if (expr === rightExpr) "__rrshift__" else null
                       PyTokenTypes.AND -> if (expr === leftExpr) "__and__" else if (expr === rightExpr) "__rand__" else null
                       PyTokenTypes.OR -> if (expr === leftExpr) "__or__" else if (expr === rightExpr) "__ror__" else null
                       PyTokenTypes.XOR -> if (expr === leftExpr) "__xor__" else if (expr === rightExpr) "__rxor__" else null
                       else -> null
                     } ?: return null

    val otherOperand = when {
                         expr === leftExpr -> rightExpr
                         expr === rightExpr -> leftExpr
                         else -> null
                       } ?: return null

    val otherOperandType = ctx.getType(otherOperand)
    val expectedResultType = getExpectedType(binaryExpr, ctx).takeUnless { it is PyAnyType } ?: PyAnyType.any
    val syntheticType = createSyntheticDunderProtocolType(expr, methodName, Ref.create(otherOperandType), expectedResultType, ctx)

    return PyUnionType.union(syntheticType, PyAnyType.any)
  }

  private fun fromComparisonExpression(expr: PyExpression, binaryExpr: PyBinaryExpression, operator: IElementType, ctx: TypeEvalContext): PyType? {
    val leftExpr = binaryExpr.leftExpression
    val rightExpr = binaryExpr.rightExpression

    val methodName = when (operator) {
                       PyTokenTypes.LT -> if (expr === leftExpr) "__lt__" else if (expr === rightExpr) "__gt__" else null
                       PyTokenTypes.GT -> if (expr === leftExpr) "__gt__" else if (expr === rightExpr) "__lt__" else null
                       PyTokenTypes.LE -> if (expr === leftExpr) "__le__" else if (expr === rightExpr) "__ge__" else null
                       PyTokenTypes.GE -> if (expr === leftExpr) "__ge__" else if (expr === rightExpr) "__le__" else null
                       PyTokenTypes.EQEQ -> if (expr === leftExpr || expr === rightExpr) "__eq__" else null

                       PyTokenTypes.NE,
                       PyTokenTypes.NE_OLD,
                         -> if (expr === leftExpr || expr === rightExpr) "__ne__" else null

                       // `x in y` / `x not in y`: the container side is constrained.
                       PyTokenTypes.IN_KEYWORD,
                       PyTokenTypes.NOT_KEYWORD,
                         -> if (expr === rightExpr) "__contains__" else null

                       // `is` / `is not` does not rely on a user-defined API.
                       PyTokenTypes.IS_KEYWORD -> null

                       else -> null
                     } ?: return null

    val argumentType = when (operator) {
      PyTokenTypes.IN_KEYWORD,
      PyTokenTypes.NOT_KEYWORD,
        -> ctx.getType(leftExpr)

      else -> when {
        expr === leftExpr && rightExpr != null -> ctx.getType(rightExpr)
        expr === rightExpr -> ctx.getType(leftExpr)
        else -> null
      }
    }

    val boolType = PyBuiltinCache.getInstance(expr).boolType ?: PyAnyType.any
    val syntheticType = createSyntheticDunderProtocolType(expr, methodName, Ref.create(argumentType), boolType, ctx)

    return when (operator) {
      PyTokenTypes.IN_KEYWORD,
      PyTokenTypes.NOT_KEYWORD,
        -> syntheticType

      else -> PyUnionType.union(syntheticType, PyAnyType.any)
    }
  }

  // TODO: Either refactor this so that [PyStructuralType] is enhanced and used here. Or refactor its callers so that we do not need
  //       to create a synthetic protocol types at all, but instead use an naive approach to compute an expected type.
  private fun createSyntheticDunderProtocolType(
    anchor: PsiElement,
    methodName: String,
    argumentType: Ref<PyType?>?,
    returnType: PyType?,
    ctx: TypeEvalContext,
  ): PyType {
    val argumentTypeText = renderTypeHint(argumentType?.get(), ctx)
    val argumentText = if (argumentType == null) "" else """, other: $argumentTypeText, /"""
    val returnTypeText = renderTypeHint(returnType, ctx)
    val text = """
    import typing
    class _Supports$methodName(typing.Protocol):
      def $methodName(self$argumentText) -> $returnTypeText: ...
    """.trimIndent()

    val codeFragment = PyExpressionCodeFragmentImpl(anchor.project, "dummy.py", text, false)
    codeFragment.context = anchor.containingFile

    val cls = PsiTreeUtil.findChildOfType(codeFragment, PyClass::class.java)
              ?: error("Failed to create synthetic protocol class from text:\n$text")

    return PyClassTypeImpl(cls, false)
  }

  private fun isSequenceLike(type: PyType?): Boolean {
    if (type == null) return false
    if (type is PyCollectionType) return true
    return type.name == PyNames.TYPE_STR || type.name == PyNames.BYTES || type.name == PyNames.TYPE_BYTEARRAY
  }

  private fun renderTypeHint(type: PyType?, ctx: TypeEvalContext): String {
    if (type == null) return "Any"
    return PythonDocumentationProvider.getTypeHint(type, ctx)
  }

  private fun createIterableType(anchor: PsiElement, elementType: PyType?): PyClassTypeImpl? {
    return PyCollectionTypeImpl.createTypeByQName(anchor, "typing." + PyNames.ITERABLE, false, listOf(elementType))
  }
}
