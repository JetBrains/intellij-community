// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.controlflow

import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.Stack
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.Companion.getType
import com.jetbrains.python.psi.PyAssertStatement
import com.jetbrains.python.psi.PyAssignmentExpression
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCaseClause
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyConditionalExpression
import com.jetbrains.python.psi.PyConditionalStatementPart
import com.jetbrains.python.psi.PyDisjointBaseUtil.areDisjoint
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyGeneratorExpression
import com.jetbrains.python.psi.PyKnownDecorator
import com.jetbrains.python.psi.PyKnownDecoratorUtil
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyMatchStatement
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PySetLiteralExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.impl.PyBuiltinCache.Companion.getInstance
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyInstantiableType
import com.jetbrains.python.psi.types.PyIntersectionType.Companion.intersection
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyLiteralType.Companion.isNone
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.PyStructuralType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeChecker.isUnknown
import com.jetbrains.python.psi.types.PyTypeUtil.inheritsAny
import com.jetbrains.python.psi.types.PyTypeUtil.toStream
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.isNoneType
import org.jetbrains.annotations.ApiStatus

class PyTypeAssertionEvaluator(private var myPositive: Boolean) : PyRecursiveElementVisitor() {
  private val myStack = Stack<Assertion>()

  val definitions: List<Assertion>
    get() = myStack

  override fun visitPyCallExpression(node: PyCallExpression) {
    if (node.isCalleeText(PyNames.ISINSTANCE, PyNames.ASSERT_IS_INSTANCE)) {
      val args = node.arguments
      if (args.size == 2) {
        val typeElement = args[1]

        pushAssertion(args[0], myPositive) { context ->
          if (myPositive || isSafeForNegativeAssertion(typeElement, context)) {
            transformTypeFromAssertion(
              context.getType(typeElement),
              false,
              context,
              typeElement
            )
          }
          else null
        }
      }
    }
    else if (node.isCalleeText(PyNames.ISSUBCLASS)) {
      val args = node.arguments
      if (args.size == 2) {
        val typeElement = args[1]

        pushAssertion(args[0], myPositive) { context ->
          if (myPositive || isSafeForNegativeAssertion(typeElement, context)) {
            transformTypeFromAssertion(
              context.getType(typeElement),
              true,
              context,
              typeElement
            )
          }
          else null
        }
      }
    }
  }

  private fun visitExpressionInCondition(node: PyExpression) {
    if (myPositive && isReferenceInTruthyCondition(node)) {
      // TODO: we can actually check if the class defines __bool__ or __len__, and use it to exclude the type
      // we could not suggest `None` because it could be a reference to an empty collection
      // so we could push only non-`None` assertions
      pushAssertion(node, !myPositive) { getInstance(node).noneType }
    }
  }

  override fun visitPyReferenceExpression(node: PyReferenceExpression) {
    visitExpressionInCondition(node)
    super.visitPyReferenceExpression(node)
  }

  override fun visitPyAssignmentExpression(node: PyAssignmentExpression) {
    visitExpressionInCondition(node)
    super.visitPyAssignmentExpression(node)
  }

  override fun visitPyBinaryExpression(node: PyBinaryExpression) {
    val lhs = PyPsiUtils.flattenParens(node.leftExpression)
    val rhs = PyPsiUtils.flattenParens(node.rightExpression)
    if (lhs == null || rhs == null) return

    val operator = node.operator
    val isOrEqualsOperator = node.isOperator(PyNames.IS) || PyTokenTypes.EQEQ == operator
    if (isOrEqualsOperator || node.isOperator("isnot") || PyTokenTypes.NE == operator || PyTokenTypes.NE_OLD == operator) {
      setPositive(isOrEqualsOperator) { processIsOrEquals(lhs, rhs) }
    }

    if (PyTokenTypes.IN_KEYWORD == operator || node.isOperator("notin")) {
      setPositive(PyTokenTypes.IN_KEYWORD == operator) { processIn(lhs, rhs) }
    }
  }

  private fun processIsOrEquals(lhs: PyExpression, rhs: PyExpression) {
    val leftBoolean = PyEvaluator.evaluateNoResolve(lhs, Boolean::class.java)
    if (leftBoolean != null) {
      setPositive(leftBoolean) { rhs.accept(this) }
      return
    }

    val rightBoolean = PyEvaluator.evaluateNoResolve(rhs, Boolean::class.java)
    if (rightBoolean != null) {
      setPositive(rightBoolean) { lhs.accept(this) }
      return
    }

    if (isNone(lhs)) {
      pushAssertion(rhs, myPositive) { getInstance(rhs).noneType }
      return
    }

    if (isNone(rhs)) {
      pushAssertion(lhs, myPositive) { getInstance(lhs).noneType }
      return
    }
    val positive = myPositive
    pushAssertion(lhs, myPositive) { context ->
      val type: PyType? = getLiteralType(rhs, context)
      if (positive || type is PyLiteralType) type else null
    }
  }

  private fun processIn(lhs: PyExpression, rhs: PyExpression) {
    if (rhs is PyTupleExpression || rhs is PyListLiteralExpression || rhs is PySetLiteralExpression) {
      val positive = myPositive
      pushAssertion(lhs, myPositive) { context ->
        val elements = rhs.elements
        val types: MutableList<PyType?> = ArrayList(elements.size)
        val noneType = getInstance(rhs).noneType
        for (element in elements) {
          val type: PyType? = if (isNone(element)) noneType else getLiteralType(element, context)
          if (type != null && (positive || type === noneType || type is PyLiteralType)) {
            types.add(type)
          }
        }
        PyUnionType.union(types)
      }
    }
  }

  private fun setPositive(positive: Boolean, runnable: () -> Unit) {
    val oldPositive = myPositive
    if (!positive) {
      myPositive = !myPositive
    }
    try {
      runnable()
    }
    finally {
      myPositive = oldPositive
    }
  }

  override fun visitPyCaseClause(node: PyCaseClause) {
    val pattern = node.pattern
    if (pattern == null) return
    val matchStatement = node.parent
    if (matchStatement is PyMatchStatement) {
      pushAssertion(matchStatement.subject, myPositive) { context -> context.getType(pattern) }
    }
  }

  /**
   * Negative type assertion for when all cases fail
   */
  override fun visitPyMatchStatement(matchStatement: PyMatchStatement) {
    assert(
      !myPositive // for match statement as a whole, only negative assertion can be made
    )
    val subject = matchStatement.subject
    if (subject == null) return
    // allowAnyExpr is here because we need negative edges with Never even when subject is not reference expression
    pushAssertion(subject, true, true, true) { context ->
      val clauses = matchStatement.caseClauses
      if (!clauses.isEmpty()) {
        clauses.last().getSubjectTypeAfter(context)
      }
      else null
    }
  }

  private fun pushAssertion(expr: PyExpression?, positive: Boolean, suggestedType: (TypeEvalContext) -> PyType?) {
    pushAssertion(expr, positive, false, isStrictNarrowingAllowed, suggestedType)
  }

  private fun pushAssertion(
    expr: PyExpression?,
    positive: Boolean,
    allowAnyExpr: Boolean,
    forceStrictNarrow: Boolean,
    suggestedType: (TypeEvalContext) -> PyType?,
  ) {
    var expr = expr
    expr = PyPsiUtils.flattenParens(expr)
    if (expr is PySequenceExpression) {
      val elements = expr.elements
      for (i in elements.indices) {
        pushAssertion(elements[i], positive, allowAnyExpr, forceStrictNarrow, getIteratedType(suggestedType, i))
      }
    }
    else if (expr is PyAssignmentExpression) {
      pushAssertion(expr.target, positive, allowAnyExpr, forceStrictNarrow, suggestedType)
    }
    else if (expr != null) {
      val target: PyExpression = expr
      val typeCallback = InstructionTypeCallback { context ->
        createAssertionType(
          context.getType(target),
          suggestedType(context),
          positive,
          forceStrictNarrow,
          context
        )
      }

      if (expr is PyReferenceExpression || expr is PyTargetExpression) {
        myStack.push(Assertion(target as PyQualifiedExpression, typeCallback))
      }
      else if (allowAnyExpr) {
        myStack.push(Assertion(null, typeCallback))
      }
    }
  }

  class Assertion(@JvmField val element: PyQualifiedExpression?, val typeEvalFunction: InstructionTypeCallback)
  companion object {
    private fun isSafeForNegativeAssertion(expression: PyExpression, context: TypeEvalContext): Boolean {
      val elements: MutableList<PyExpression> = expandClassInfoExpressions(expression)
      if (elements.isEmpty()) return false
      for (element in elements) {
        if (!isSafeClassInfoReference(element, context)) {
          return false
        }
      }
      return true
    }

    @ApiStatus.Internal
    @JvmStatic
    fun expandClassInfoExpressions(expression: PyExpression): MutableList<PyExpression> {
      val flattened = PyPsiUtils.flattenParens(expression)
      if (flattened == null) return mutableListOf()
      if (flattened is PyTupleExpression) {
        val result: MutableList<PyExpression> = ArrayList()
        for (element in flattened.elements) {
          result.addAll(expandClassInfoExpressions(element))
        }
        return result
      }
      // Keep in mind that `isinstance` will only accept `A | B` expression if all operands are classinfo, so no parameterized generics
      if (flattened is PyBinaryExpression && flattened.operator === PyTokenTypes.OR) {
        val left = flattened.leftExpression
        val right = flattened.rightExpression
        val result: MutableList<PyExpression> = ArrayList(expandClassInfoExpressions(left))
        if (right != null) {
          result.addAll(expandClassInfoExpressions(right))
        }
        return result
      }
      return mutableListOf(flattened)
    }

    private fun isSafeClassInfoReference(expression: PyExpression, context: TypeEvalContext): Boolean {
      if (expression is PyReferenceExpression) {
        // Here we check that the reference resolves to a class, not a target in assignment or function parameter.
        // This is done to avoid cases like Py3TypeTest.testIsInstanceNegativeNarrowing
        val resolvedElements = PyUtil.multiResolveTopPriority(expression, PyResolveContext.defaultContext(context))
        return resolvedElements.singleOrNull() is PyClass
      }
      return false
    }

    private fun getLiteralType(element: PyExpression, context: TypeEvalContext): PyType? {
      var type = PyLiteralType.getLiteralType(element, context)
      if (type == null) {
        type = context.getType(element)
      }
      return if (type.toStream().allMatch { it is PyLiteralType }) type else null
    }

    @JvmStatic
    @ApiStatus.Internal
    fun createAssertionType(
      initial: PyType?,
      suggested: PyType?,
      positive: Boolean,
      forceStrictNarrow: Boolean,
      context: TypeEvalContext,
    ): Ref<PyType?>? {
      if (suggested == null) return null
      if (positive) {
        // Find all initial type members that are subtypes of the suggested (more specific than the narrowing "suggested" type).
        // Imagine having `list[int] | int` narrowed by `list[Any]`.
        val initialSubtypes = initial.toStream()
          .filter { initialSubtype -> match(suggested, initialSubtype, context) }
          .toList()

        // Find all suggested subtype members that are subtypes of the initial (more specific than the initial type)
        // AND are not subtypes of those more specific initial types.
        // This is needed to support generics of `Any`, because `list[Any]` is both a subtype, and a supertype of `list[str]`.
        val suggestedSubtypes = suggested.toStream()
          .filter { suggestedSubtype -> match(initial, suggestedSubtype, context) }
          .filter { suggestedSubtype ->
            !initialSubtypes.any { initialSubtype -> match(initialSubtype, suggestedSubtype, context) }
          }

        val types = initialSubtypes + suggestedSubtypes
        return Ref(if (types.isEmpty()) intersect(initial, suggested, context) else PyUnionType.union(types))
      }
      else {
        if (initial is PyUnionType) {
          return Ref(excludeFromUnion(initial, suggested, context, forceStrictNarrow))
        }
        if (match(suggested, initial, context)) {
          return if (forceStrictNarrow || isStrictNarrowingAllowed) Ref(PyNeverType.NEVER) else null
        }
        val diff = trySubtract(initial, suggested, context)
        return diff ?: Ref(initial)
      }
    }

    private fun excludeFromUnion(
      unionType: PyUnionType,
      type: PyType?,
      context: TypeEvalContext,
      forceStrictNarrow: Boolean,
    ): PyType? {
      val members: MutableList<PyType?> = ArrayList()
      for (m in unionType.members) {
        val diff = trySubtract(m, type, context)
        if (diff != null) {
          members.add(diff.get())
        }
        else if (!match(type, m, context)) {
          members.add(m)
        }
      }
      if ((forceStrictNarrow || isStrictNarrowingAllowed) && members.isEmpty()) {
        return PyNeverType.NEVER
      }
      return PyUnionType.union(members)
    }

    val isStrictNarrowingAllowed: Boolean
      get() = Registry.`is`("python.strict.type.narrow")

    private fun trySubtract(
      type1: PyType?,
      type2: PyType?,
      context: TypeEvalContext,
    ): Ref<PyType?>? {
      assert(type1 !is PyUnionType)

      if ((type1 !is PyLiteralType) &&
          type1 is PyClassType &&
          PyStdlibTypeProvider.isCustomEnum(type1.pyClass, context)
      ) {
        if (type1.pyClass.getAncestorClasses(context)
            .any { cls -> PyNames.TYPE_ENUM_FLAG == cls!!.qualifiedName }
        ) {
          // Do not expand enum classes that derive from enum.Flag
          return null
        }
        val enumMembers = PyStdlibTypeProvider.getEnumMembers(type1.pyClass, context)!!.toList()
        val filteredEnumMembers =
          enumMembers.filter { m -> !PyTypeChecker.match(type2, m, context) }
        if (filteredEnumMembers.isEmpty()) {
          return Ref(PyNeverType.NEVER)
        }
        val type = if (enumMembers.size == filteredEnumMembers.size) type1 else PyUnionType.union(filteredEnumMembers)
        return Ref(type)
      }
      return null
    }


    private fun isFinal(classType: PyClassType, context: TypeEvalContext): Boolean {
      val deco = PyKnownDecoratorUtil.getKnownDecorators(classType.pyClass, context)
      return deco.contains(PyKnownDecorator.TYPING_FINAL) || deco.contains(PyKnownDecorator.TYPING_FINAL_EXT)
    }

    private fun isLiteralOrNoneType(type: PyType?): Boolean {
      return type is PyLiteralType || type.isNoneType
    }

    private fun intersect(
      initial: PyType?,
      suggested: PyType?,
      context: TypeEvalContext,
    ): PyType? {
      if (isLiteralOrNoneType(initial) && PyTypeChecker.match(suggested, initial, context)) {
        return initial
      }
      if (isLiteralOrNoneType(suggested) && PyTypeChecker.match(initial, suggested, context)) {
        return suggested
      }

      if (initial is PyClassType && suggested is PyClassType) {
        if (areDisjoint(initial, suggested, context)) {
          return PyNeverType.NEVER
        }
        if (isFinal(initial, context) || isFinal(suggested, context)) {
          if (!PyTypeChecker.match(initial, suggested, context) && !PyTypeChecker.match(suggested, initial, context)) {
            return PyNeverType.NEVER
          }
        }
        return intersection(initial, suggested)
      }

      if (initial is PyUnionType && initial.members.size <= 5) {
        return initial.map { member -> intersect(member, suggested, context) }
      }
      if (suggested is PyUnionType && suggested.members.size <= 5) {
        return suggested.map { member -> intersect(initial, member, context) }
      }
      if (isLiteralOrNoneType(initial) || isLiteralOrNoneType(suggested)) {
        return PyNeverType.NEVER
      }
      return intersection(initial, suggested)
    }

    private fun match(expected: PyType?, actual: PyType?, context: TypeEvalContext): Boolean {
      return (actual !is PyStructuralType) && !isUnknown(actual, context) && !(actual.inheritsAny(context)) &&
             PyTypeChecker.match(expected, actual, context)
    }

    /**
     * @param transformToDefinition if true the result type will be Type[T], not T itself.
     */
    private fun transformTypeFromAssertion(
      type: PyType?,
      transformToDefinition: Boolean,
      context: TypeEvalContext,
      typeElement: PyExpression?,
    ): PyType? {
      /*
       * We need to distinguish:
       *   if isinstance(x, (int, str)):
       * And:
       *   if isinstance(x, (1, "")):
       */
      val typeElementNoParens = PyPsiUtils.flattenParens(typeElement)
      if (type is PyTupleType) {
        val members: MutableList<PyType?> = ArrayList()
        val count = type.elementCount

        val tupleExpression = typeElementNoParens as? PyTupleExpression
        if (tupleExpression != null && tupleExpression.elements.size == count) {
          val elements = tupleExpression.elements
          for (i in 0..<count) {
            members.add(transformTypeFromAssertion(type.getElementType(i), transformToDefinition, context, elements[i]))
          }
        }
        else {
          for (i in 0..<count) {
            members.add(transformTypeFromAssertion(type.getElementType(i), transformToDefinition, context, null))
          }
        }

        return PyUnionType.union(members)
      }
      else if (typeElementNoParens is PyBinaryExpression && typeElementNoParens.operator === PyTokenTypes.OR) {
        val typeFromTypingProvider = getType(typeElementNoParens, context)
        if (typeFromTypingProvider != null) {
          return transformTypeFromAssertion(typeFromTypingProvider.get(), transformToDefinition, context, null)
        }
      }
      else if (type is PyUnionType) {
        return type.map { member: PyType? -> transformTypeFromAssertion(member, transformToDefinition, context, null) }
      }
      else if (type is PyInstantiableType<*>) {
        return if (transformToDefinition) type.toClass() else type.toInstance()
      }
      return type
    }

    private fun getIteratedType(sequenceType: (TypeEvalContext) -> PyType?, index: Int): (TypeEvalContext) -> PyType? {
      return { context ->
        val computedSuggestedType = sequenceType(context)
        when (computedSuggestedType) {
          is PyNeverType -> computedSuggestedType
          is PyTupleType -> {
            computedSuggestedType.getElementType(index)
          }
          else -> null
        }
      }
    }

    private fun skipNotAndParens(element: PsiElement?): PsiElement? {
      if (element == null) return null
      var e = element.parent
      while (e != null) {
        if (e !is PyParenthesizedExpression &&
            !(e is PyPrefixExpression && e.operator === PyTokenTypes.NOT_KEYWORD)
        ) {
          return e
        }
        e = e.parent
      }
      return null
    }

    private fun isReferenceInTruthyCondition(node: PyExpression): Boolean {
      val parent: PsiElement? = skipNotAndParens(node)
      if (parent is PyConditionalStatementPart) return true
      if (parent is PyConditionalExpression && PsiTreeUtil.isAncestor(parent.condition, node, false)) return true
      if (parent is PyBinaryExpression && (parent.isOperator(PyNames.AND) || parent.isOperator(PyNames.OR))) return true
      if (parent is PyAssertStatement) return true
      if (parent is PyGeneratorExpression &&
          parent.getIfComponents().any { PsiTreeUtil.isAncestor(it.getTest(), node, false) }
      ) {
        return true
      }
      return false
    }
  }
}
