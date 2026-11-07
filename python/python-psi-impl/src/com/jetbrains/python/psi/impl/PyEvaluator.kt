// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonFQDNNames
import com.jetbrains.python.nameResolver.NameResolverTools
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyBoolLiteralExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyConditionalExpression
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyElementType
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFStringFragment
import com.jetbrains.python.psi.PyFormattedStringElement
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyNumericLiteralExpression
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PySliceItem
import com.jetbrains.python.psi.PyStarArgument
import com.jetbrains.python.psi.PyStringElement
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import java.math.BigInteger

/**
 * TODO: Merge PythonDataflowUtil and [PyEvaluator] and all its inheritors and improve Abstract Interpretation
 */
open class PyEvaluator {

  /**
   * The expressions on the current evaluation path, each with its depth on the path. A reference to one of them is a cycle,
   * and the evaluator cuts it.
   */
  private val myEvaluating = HashMap<PyExpression, Int>()

  /**
   * The lowest path depth of a cut in the expression that the evaluator evaluates now.
   */
  private var myLowestCutDepth = Int.MAX_VALUE

  /**
   * The results of the evaluated expressions. A result that depends on a cut above its expression is not kept,
   * because the value of the cut expression is not known yet.
   */
  private val myResults = HashMap<PyExpression, Any?>()

  private var myNamespace: Map<String, Any?>? = null

  private var myPythonVersion: IntArray? = null

  /**
   * if true, collection items or dict values will be evaluated
   */
  private var myEvaluateCollectionItems = true

  /**
   * if true, dict keys will be evaluated
   */
  private var myEvaluateKeys = true

  /**
   * if true, references will be resolved
   */
  private var myEnableResolve = true

  /**
   * Store expressions as values if they can't be evaludated
   */
  private var myAllowExpressionsAsValues = false

  fun setNamespace(namespace: Map<String, Any?>?) {
    myNamespace = namespace
    myResults.clear()
  }

  fun setLanguageLevel(languageLevel: LanguageLevel) {
    myPythonVersion = intArrayOf(languageLevel.majorVersion, languageLevel.minorVersion)
    myResults.clear()
  }

  fun setEvaluateCollectionItems(evaluateCollectionItems: Boolean) {
    myEvaluateCollectionItems = evaluateCollectionItems
    myResults.clear()
  }

  fun setAllowExpressionsAsValues(allowExpressionsAsValues: Boolean) {
    myAllowExpressionsAsValues = allowExpressionsAsValues
    myResults.clear()
  }

  fun setEvaluateKeys(evaluateKeys: Boolean) {
    myEvaluateKeys = evaluateKeys
    myResults.clear()
  }

  fun enableResolve(enableResolve: Boolean) {
    myEnableResolve = enableResolve
    myResults.clear()
  }

  @Contract("null -> null")
  open fun evaluate(expression: PyExpression?): Any? {
    if (expression == null) {
      return null
    }
    if (myResults.containsKey(expression)) {
      return myResults[expression]
    }
    val cutDepth = myEvaluating[expression]
    if (cutDepth != null) {
      myLowestCutDepth = minOf(myLowestCutDepth, cutDepth)
      return null
    }
    val depth = myEvaluating.size
    val outerLowestCutDepth = myLowestCutDepth
    myEvaluating[expression] = depth
    myLowestCutDepth = Int.MAX_VALUE
    try {
      ProgressManager.checkCanceled()
      PyUtil.verboseOnly { PyPsiUtils.assertValid(expression) }
      val result = evaluateOnce(expression)
      if (myLowestCutDepth >= depth) {
        myResults[expression] = result
      }
      return result
    }
    finally {
      myEvaluating.remove(expression)
      myLowestCutDepth = minOf(outerLowestCutDepth, if (myLowestCutDepth < depth) myLowestCutDepth else Int.MAX_VALUE)
    }
  }

  private fun evaluateOnce(expression: PyExpression): Any? {
    when (expression) {
      is PyParenthesizedExpression -> return evaluate(expression.containedExpression)
      is PySequenceExpression -> return evaluateSequence(expression)
    }
    getBooleanLiteralValue(expression)?.let { return it }
    return when (expression) {
      is PyCallExpression -> evaluateCall(expression)
      is PyReferenceExpression -> evaluateReference(expression)
      is PyStringLiteralExpression -> evaluateString(expression)
      is PyBinaryExpression -> evaluateBinary(expression)
      is PyNumericLiteralExpression -> evaluateNumeric(expression)
      is PyPrefixExpression -> evaluatePrefix(expression)
      is PyConditionalExpression -> evaluateConditional(expression)
      is PySubscriptionExpression -> evaluateSubscription(expression)
      else -> null
    }
  }

  private fun evaluateConditional(expression: PyConditionalExpression): Any? {
    val condition = evaluateTruthiness(expression.condition) ?: return null
    return evaluate(if (condition) expression.truePart else expression.falsePart)
  }

  private fun evaluateSubscription(expression: PySubscriptionExpression): Any? {
    val operand = toConstant(evaluate(expression.operand)) ?: return null
    val index = expression.indexExpression ?: return null
    if (index is PySliceItem) {
      val bounds = listOf(index.lowerBound, index.upperBound, index.stride).map { bound ->
        if (bound == null) null else toConstant(evaluate(bound))?.let(PyConstantFolder::asInteger) ?: return null
      }
      return fromConstant(PyConstantFolder.slice(operand, bounds[0], bounds[1], bounds[2]))
    }
    return fromConstant(PyConstantFolder.subscript(operand, toConstant(evaluate(index)) ?: return null))
  }

  /**
   * Returns `null` for a bytes literal, a t-string and an f-string with a fragment that does not fold. Their values are not a `str`
   * that the evaluator knows.
   */
  private fun evaluateString(expression: PyStringLiteralExpression): String? {
    val result = StringBuilder()
    var folded = false
    for (element in expression.stringElements) {
      if (element.isBytes || element.isTemplate || hasUnsupportedEscape(element)) {
        return null
      }
      if (element is PyFormattedStringElement && element.fragments.isNotEmpty()) {
        folded = true
        result.append(evaluateFormattedString(element) ?: return null)
      }
      else if (!appendDecoded(element, element.decodedFragments, result)) {
        return null
      }
      if (folded && result.length > PyConstantFolder.MAX_STRING_LENGTH) {
        return null
      }
    }
    return result.toString()
  }

  private fun evaluateFormattedString(element: PyFormattedStringElement): String? {
    val decoder = PyStringLiteralDecoder(element)
    val result = StringBuilder()
    var child = element.firstChild
    while (child != null) {
      if (PyTokenTypes.FSTRING_TEXT_TOKENS.contains(child.node.elementType)) {
        if (!appendDecoded(element, decoder.decodeRange(child.textRangeInParent), result)) {
          return null
        }
      }
      else if (child is PyFStringFragment) {
        result.append(evaluateFragment(child) ?: return null)
      }
      if (result.length > PyConstantFolder.MAX_STRING_LENGTH) {
        return null
      }
      child = child.nextSibling
    }
    return result.toString()
  }

  /**
   * Evaluates a replacement field without a format specifier, a `=` or a conversion other than `!s`.
   */
  private fun evaluateFragment(fragment: PyFStringFragment): String? {
    val conversion = fragment.typeConversion?.text
    if (fragment.formatPart != null || (conversion != null && conversion != "!s")) {
      return null
    }
    val expression = fragment.expression ?: return null
    val textAfterExpression = fragment.text.substring(expression.startOffsetInParent + expression.textLength)
    if (textAfterExpression.trimStart().startsWith("=")) {
      return null
    }
    return toConstant(evaluate(expression))?.let(PyConstantFolder::toStr)
  }

  protected open fun evaluateBinary(expression: PyBinaryExpression): Any? {
    val op = expression.operator

    val pythonVersion = myPythonVersion
    if (op != null && pythonVersion != null && PyTokenTypes.RELATIONAL_OPERATIONS.contains(op)) {
      if (isSysVersionInfoExpression(expression.leftExpression)) {
        val version = evaluateAsVersion(expression.rightExpression)
        if (version != null) {
          return evaluateVersionCheck(op, pythonVersion, version)
        }
      }
      else if (isSysVersionInfoExpression(expression.rightExpression)) {
        val version = evaluateAsVersion(expression.leftExpression)
        if (version != null) {
          return evaluateVersionCheck(op, version, pythonVersion)
        }
      }
    }

    if (op == null) {
      return null
    }
    if (isComparison(expression)) {
      return evaluateComparisonChain(expression)
    }
    val lhs = evaluate(expression.leftExpression)
    val rhs = evaluate(expression.rightExpression)
    if (op === PyTokenTypes.PLUS && lhs != null && rhs != null) {
      return applyPlus(lhs, rhs)
    }
    if (op === PyTokenTypes.AND_KEYWORD || op === PyTokenTypes.OR_KEYWORD) {
      return fromConstant(PyConstantFolder.binary(op, toConstant(lhs), toConstant(rhs)))
    }
    return fromConstant(PyConstantFolder.binary(op, toConstant(lhs) ?: return null, toConstant(rhs) ?: return null))
  }

  /**
   * Evaluates `a < b < c` as Python does: `a < b and b < c`, with `b` evaluated once. The parser nests a chain to the left,
   * as `(a < b) < c`. A comparison in parentheses is not a part of the chain.
   */
  private fun evaluateComparisonChain(expression: PyBinaryExpression): Boolean? {
    val links = ArrayList<PyBinaryExpression>()
    var first: PyExpression? = expression
    while (first is PyBinaryExpression && isComparison(first)) {
      links.add(first)
      first = first.leftExpression
    }
    links.reverse()
    var left = evaluate(first)
    var unknown = false
    for (link in links) {
      val right = evaluate(link.rightExpression)
      when (compare(link, left, right)) {
        false -> return false
        null -> unknown = true
        true -> {}
      }
      left = right
    }
    return if (unknown) null else true
  }

  private fun compare(link: PyBinaryExpression, lhs: Any?, rhs: Any?): Boolean? {
    val op = link.operator ?: return null
    val left = toConstant(lhs) ?: return null
    val right = toConstant(rhs) ?: return null
    return when {
      op === PyTokenTypes.IN_KEYWORD -> PyConstantFolder.contains(right, left)
      op === PyTokenTypes.NOT_KEYWORD -> PyConstantFolder.contains(right, left)?.not()
      op === PyTokenTypes.IS_KEYWORD -> null
      else -> (PyConstantFolder.binary(op, left, right) as? PyConstantValue.Bool)?.value
    }
  }

  /**
   * Returns the result of `bool(expression)`. `and`, `or` and `not` give a result also when an operand is not known,
   * if the known operand decides it: `x or True` is true for each `x`.
   */
  private fun evaluateTruthiness(expression: PyExpression?): Boolean? {
    val flattened = PyPsiUtils.flattenParens(expression) ?: return null
    if (flattened is PyBinaryExpression && flattened.isShortCircuit) {
      val left = evaluateTruthiness(flattened.leftExpression)
      val right = evaluateTruthiness(flattened.rightExpression)
      val isOr = flattened.operator === PyTokenTypes.OR_KEYWORD
      return when {
        left == isOr || right == isOr -> isOr
        left != null && right != null -> !isOr
        else -> null
      }
    }
    if (flattened is PyPrefixExpression && flattened.operator === PyTokenTypes.NOT_KEYWORD) {
      return evaluateTruthiness(flattened.operand)?.not()
    }
    return isTruthy(evaluate(flattened))
  }

  private fun evaluatePrefix(expression: PyPrefixExpression): Any? {
    val operator = expression.operator
    if (!PyConstantFolder.isPrefixOperator(operator)) {
      return null
    }
    return fromConstant(PyConstantFolder.prefix(operator, toConstant(evaluate(expression.operand))))
  }

  /**
   * Evaluates sequence (tuple, list, set, dict)
   *
   * @param expression sequence expression
   * @return evaluated sequence
   */
  protected open fun evaluateSequence(expression: PySequenceExpression): Any {
    if (expression is PyDictLiteralExpression) {
      val result = HashMap<Any?, Any?>()
      for (keyValue in expression.elements) {
        addRecordFromDict(result, keyValue.key, keyValue.value)
      }
      return result
    }

    val items = expression.elements.map { element -> if (myEvaluateCollectionItems) evaluate(element) else element }
    return when (expression) {
      is PyTupleExpression -> TupleItems(items)
      is PyListLiteralExpression -> ListItems(items)
      else -> items
    }
  }

  /**
   * Joins two lists also when Python raises an error, as `list + tuple` does, because [PyBlockEvaluator] uses this method
   * for `x += y` and `x.extend(y)`. The result folds further only when both lists are of one kind.
   */
  open fun applyPlus(lhs: Any?, rhs: Any?): Any? {
    if (lhs is List<*> && rhs is List<*>) {
      return when {
        lhs is TupleItems && rhs is TupleItems -> TupleItems(lhs + rhs)
        lhs is ListItems && rhs is ListItems -> ListItems(lhs + rhs)
        else -> ContainerUtil.concat(lhs, rhs)
      }
    }
    return fromConstant(PyConstantFolder.binary(PyTokenTypes.PLUS, toConstant(lhs), toConstant(rhs)))
  }

  protected open fun evaluateReference(expression: PyReferenceExpression): Any? {
    if (isTypeCheckingExpression(expression)) {
      return true
    }
    if (!expression.isQualified) {
      val namespace = myNamespace
      if (namespace != null) {
        return expression.referencedName?.let { namespace[it] }
      }
      if (!myEnableResolve) {
        return null
      }
      val context = TypeEvalContext.codeInsightFallback(expression.project)
      val results = expression.getReference(PyResolveContext.defaultContext(context)).multiResolve(false)
      if (results.size != 1) {
        return null
      }
      var result = results[0].element
      if (result is PyTargetExpression) {
        result = result.findAssignedValue()
      }
      if (result is PyExpression) {
        return evaluate(result)
      }
    }
    return null
  }

  /**
   * Evaluates a method call on a `str` value, a call of a builtin function and `dict(...)`. A method call on a `str` needs no resolve,
   * because no code can change the methods of `str`. A builtin function needs resolve, because a module can shadow its name.
   */
  protected open fun evaluateCall(expression: PyCallExpression): Any? {
    val callee = expression.callee
    if (callee is PyReferenceExpression) {
      val name = callee.referencedName
      val qualifier = callee.qualifier
      if (name != null && qualifier != null) {
        val receiver = toConstant(evaluate(qualifier))
        if (receiver is PyConstantValue.Str) {
          val arguments = evaluatePositionalArguments(expression) ?: return null
          return fromConstant(PyConstantFolder.callMethod(receiver, name, arguments))
        }
      }
      else if (name in FOLDED_BUILTINS && myEnableResolve && PyBuiltinCache.isInBuiltins(callee)) {
        val arguments = evaluatePositionalArguments(expression) ?: return null
        return fromConstant(PyConstantFolder.callBuiltin(name!!, arguments))
      }
    }

    if (myEnableResolve && NameResolverTools.isCalleeShortCut(expression, PythonFQDNNames.DICT_CLASS)) {
      return evaluateDictCall(expression)
    }
    return null
  }

  /**
   * Evaluates `dict([("k", "v")], foo="spam")`: the pairs of a sequence literal and the keyword arguments.
   * The result skips the other arguments, so it can hold only a part of the keys. Template completion uses such a part.
   */
  private fun evaluateDictCall(expression: PyCallExpression): Map<Any?, Any?>? {
    val arguments = expression.arguments
    if (arguments.isEmpty()) {
      return null
    }
    val result = HashMap<Any?, Any?>()
    for (argument in arguments) {
      if (argument is PyKeywordArgument) {
        val keyword = argument.keyword
        if (keyword != null) {
          addRecordFromDict(result, keyword, argument.valueExpression)
        }
      }
      else if (!addRecordsFromPairs(result, argument)) {
        return null
      }
    }
    return result
  }

  /**
   * Returns `false` for a sequence literal with an element that is not a pair. Another argument adds nothing.
   */
  private fun addRecordsFromPairs(result: MutableMap<Any?, Any?>, argument: PyExpression): Boolean {
    val pairs = PyPsiUtils.flattenParens(argument)
    if (pairs !is PySequenceExpression || pairs is PyDictLiteralExpression) {
      return true
    }
    for (pair in pairs.elements) {
      val tuple = PyPsiUtils.flattenParens(pair)
      if (tuple !is PyTupleExpression || tuple.elements.size != 2) {
        return false
      }
      addRecordFromDict(result, tuple.elements[0], tuple.elements[1])
    }
    return true
  }

  private fun addRecordFromDict(result: MutableMap<Any?, Any?>, key: PyExpression, value: PyExpression?) {
    result[if (myEvaluateKeys) evaluate(key) else key] = if (myEvaluateCollectionItems) evaluateOrGet(value) else value
  }

  private fun addRecordFromDict(result: MutableMap<Any?, Any?>, key: String, value: PyExpression?) {
    result[key] = if (myEvaluateCollectionItems) evaluateOrGet(value) else value
  }

  /**
   * Returns `null` for a call with a keyword argument or an unpacked argument.
   */
  private fun evaluatePositionalArguments(expression: PyCallExpression): List<PyConstantValue?>? {
    val arguments = expression.arguments
    if (arguments.any { it is PyKeywordArgument || it is PyStarArgument }) {
      return null
    }
    return arguments.map { toConstant(evaluate(it)) }
  }

  private fun evaluateOrGet(expression: PyExpression?): Any? {
    val result = evaluate(expression)
    if (result != null) {
      return result
    }
    return if (myAllowExpressionsAsValues) expression else null
  }

  companion object {
    private val SYS_VERSION_INFO = QualifiedName.fromDottedString("sys.version_info")

    private const val TYPE_CHECKING = "TYPE_CHECKING"

    private val FOLDED_BUILTINS = setOf("len", "str", "int", "bool", "ord", "chr")

    /** A `tuple` value of the evaluator. */
    private class TupleItems(items: Collection<Any?>) : ArrayList<Any?>(items)

    /** A `list` value of the evaluator. */
    private class ListItems(items: Collection<Any?>) : ArrayList<Any?>(items)

    private fun evaluateNumeric(expression: PyNumericLiteralExpression): Any? {
      if (expression.isIntegerLiteral) {
        return fromBigInteger(expression.bigIntegerValue)
      }
      return null
    }

    @ApiStatus.Internal
    @JvmStatic
    fun evaluateAsVersion(expression: PyExpression?): IntArray? {
      val tupleExpression = PyPsiUtils.flattenParens(expression) as? PyTupleExpression ?: return null
      val elements = tupleExpression.elements
      val result = IntArray(elements.size)
      for (i in elements.indices) {
        result[i] = evaluateNoResolve(elements[i], Int::class.javaObjectType) ?: return null
      }
      return result
    }

    private fun evaluateVersionCheck(op: PyElementType, lhs: IntArray, rhs: IntArray): Boolean {
      return when (op) {
        PyTokenTypes.LT -> ArrayUtil.lexicographicCompare(lhs, rhs) < 0
        PyTokenTypes.LE -> ArrayUtil.lexicographicCompare(lhs, rhs) <= 0
        PyTokenTypes.GT -> ArrayUtil.lexicographicCompare(lhs, rhs) > 0
        PyTokenTypes.GE -> ArrayUtil.lexicographicCompare(lhs, rhs) >= 0
        else -> throw IllegalArgumentException()
      }
    }

    @ApiStatus.Internal
    @JvmStatic
    fun getBooleanLiteralValue(expression: PsiElement): Boolean? {
      if (expression is PyBoolLiteralExpression) {
        if (PyNames.DEBUG == expression.text) {
          return null
        }
        return expression.value
      }
      else if (expression is PyReferenceExpression && LanguageLevel.forElement(expression).isPython2) {
        when (expression.referencedName) {
          PyNames.TRUE -> return true
          PyNames.FALSE -> return false
        }
      }

      return null
    }

    /**
     * Converts a value of the evaluator to the form of [PyConstantFolder]. The evaluator keeps an `int` as an [Int], a [Long]
     * or a [BigInteger], whichever is the smallest that holds it.
     */
    private fun toConstant(value: Any?): PyConstantValue? {
      return toConstant(value, intArrayOf(PyConstantFolder.MAX_SEQUENCE_SIZE))
    }

    /**
     * Converts a value of the evaluator to the form of [PyConstantFolder]. Only a [TupleItems] and a [ListItems] convert to a sequence,
     * because another [List] can be a `set` or a list of a subclass that does not hold Python values. The [budget] limits the count
     * of the converted items, because the lists of nested tuples can share items.
     */
    private fun toConstant(value: Any?, budget: IntArray): PyConstantValue? {
      return when (value) {
        is String -> PyConstantValue.Str(value)
        is Boolean -> PyConstantValue.Bool(value)
        is Int -> PyConstantValue.Int(BigInteger.valueOf(value.toLong()))
        is Long -> PyConstantValue.Int(BigInteger.valueOf(value))
        is BigInteger -> PyConstantValue.Int(value)
        is TupleItems -> toSequence(value, PyConstantValue.SequenceKind.TUPLE, budget)
        is ListItems -> toSequence(value, PyConstantValue.SequenceKind.LIST, budget)
        else -> null
      }
    }

    private fun toSequence(items: List<Any?>, kind: PyConstantValue.SequenceKind, budget: IntArray): PyConstantValue.Sequence? {
      budget[0] -= items.size
      if (budget[0] < 0) {
        return null
      }
      return PyConstantValue.Sequence(items.map { toConstant(it, budget) ?: return null }, kind)
    }

    private fun fromConstant(value: PyConstantValue?): Any? {
      return when (value) {
        null -> null
        is PyConstantValue.Str -> value.value
        is PyConstantValue.Bool -> value.value
        is PyConstantValue.Int -> fromBigInteger(value.value)
        is PyConstantValue.Sequence -> when (value.kind) {
          PyConstantValue.SequenceKind.TUPLE -> TupleItems(value.items.map(::fromConstant))
          PyConstantValue.SequenceKind.LIST -> ListItems(value.items.map(::fromConstant))
        }
      }
    }

    /**
     * The parser gives a binary `not` only for `not in`.
     */
    private fun isComparison(expression: PyBinaryExpression): Boolean {
      return PyTokenTypes.COMPARISON_OPERATIONS.contains(expression.operator)
    }

    private val SURROGATE_ESCAPE = Regex("""\\(u[dD][89a-fA-F]|U0000[dD][89a-fA-F])""")

    /**
     * Appends the decoded [fragments] of [element] to [result]. Returns `false` for a `\N` escape that the decoder keeps as text
     * in a Python 3 literal that is not raw. Python gives an error or a character that the JDK does not name for such an escape.
     * A Python 2 `str` literal also keeps `\N` as text, and this check rejects it too.
     */
    private fun appendDecoded(element: PyStringElement, fragments: List<Pair<TextRange, String>>, result: StringBuilder): Boolean {
      for (fragment in fragments) {
        val value = fragment.second
        if (!element.isRaw && value.contains("\\N") && fragment.first.substring(element.text) == value) {
          return false
        }
        result.append(value)
      }
      return true
    }

    /**
     * Finds an escape that [PyStringLiteralDecoder] does not decode as Python does. Java joins a surrogate with the next surrogate
     * into one code point. In a raw f-string, the lexer keeps `\N{name}` as text, but Python reads `{name}` as a replacement field.
     */
    private fun hasUnsupportedEscape(element: PyStringElement): Boolean {
      if (element.isRaw) {
        return element.isFormatted && element.text.contains("\\N{")
      }
      return SURROGATE_ESCAPE.containsMatchIn(element.text)
    }

    private fun isTruthy(value: Any?): Boolean? {
      return when (value) {
        is Collection<*> -> !value.isEmpty()
        is Map<*, *> -> !value.isEmpty()
        else -> toConstant(value)?.let(PyConstantFolder::isTruthy)
      }
    }

    private fun fromBigInteger(value: BigInteger): Number {
      val intValue = value.toInt()
      if (BigInteger.valueOf(intValue.toLong()) == value) {
        return intValue
      }

      val longValue = value.toLong()
      if (BigInteger.valueOf(longValue) == value) {
        return longValue
      }

      return value
    }

    /**
     * Shortcut that evaluates expression with default params and casts it to particular type (if possible)
     *
     * @param T          expected type
     * @param expression expression to evaluate
     * @param resultType expected type
     * @return value if expression is evaluated to this type, null otherwise
     */
    @JvmStatic
    fun <T> evaluate(expression: PyExpression?, resultType: Class<T>): T? {
      return PyUtil.`as`(PyEvaluator().evaluate(expression), resultType)
    }

    /**
     * Shortcut that evaluates expression with default params and disabled resolve and casts it to particular type (if possible)
     *
     * @param T          expected type
     * @param expression expression to evaluate
     * @param resultType expected type
     * @return value if expression is evaluated to this type, null otherwise
     */
    @JvmStatic
    fun <T> evaluateNoResolve(expression: PyExpression?, resultType: Class<T>): T? {
      val evaluator = PyEvaluator()
      evaluator.enableResolve(false)
      return PyUtil.`as`(evaluator.evaluate(expression), resultType)
    }

    /**
     * Shortcut that evaluates expression and tries to determine if `bool` will return true for it
     *
     * @param expression expression to evaluate
     * @return true if expression is evaluated to value so `bool` returns true for it
     */
    @JvmStatic
    fun evaluateAsBoolean(expression: PyExpression?): Boolean? {
      return evaluateAsBoolean(prepareEvaluatorForBoolean(true), expression)
    }

    /**
     * Shortcut that evaluates expression and tries to determine if `bool` will return true for it
     *
     * @param expression expression to evaluate
     * @return true if expression is evaluated to value so `bool` returns true for it
     */
    @JvmStatic
    fun evaluateAsBooleanNoResolve(expression: PyExpression?): Boolean? {
      return evaluateAsBoolean(prepareEvaluatorForBoolean(false), expression)
    }

    @ApiStatus.Experimental
    @JvmStatic
    fun evaluateAsBooleanNoResolve(expression: PyExpression?, languageLevel: LanguageLevel?): Boolean? {
      val evaluator = prepareEvaluatorForBoolean(false)
      if (languageLevel != null) {
        evaluator.setLanguageLevel(languageLevel)
      }
      return evaluateAsBoolean(evaluator, expression)
    }

    @JvmStatic
    fun evaluateAsBoolean(expression: PyExpression?, defaultValue: Boolean): Boolean {
      return evaluateAsBoolean(expression) ?: defaultValue
    }

    @JvmStatic
    fun evaluateAsBooleanNoResolve(expression: PyExpression?, defaultValue: Boolean): Boolean {
      return evaluateAsBooleanNoResolve(expression) ?: defaultValue
    }

    private fun prepareEvaluatorForBoolean(enableResolve: Boolean): PyEvaluator {
      val evaluator = PyEvaluator()
      evaluator.setEvaluateCollectionItems(false)
      evaluator.setEvaluateKeys(false)
      evaluator.enableResolve(enableResolve)
      return evaluator
    }

    private fun evaluateAsBoolean(evaluator: PyEvaluator, expression: PyExpression?): Boolean? {
      return evaluator.evaluateTruthiness(expression)
    }

    @ApiStatus.Internal
    @JvmStatic
    fun isSysVersionInfoExpression(expression: PyExpression?): Boolean {
      val referenceExpression = PyPsiUtils.flattenParens(expression) as? PyReferenceExpression ?: return false
      return SYS_VERSION_INFO == referenceExpression.asQualifiedName()
    }

    @ApiStatus.Internal
    @JvmStatic
    fun isTypeCheckingExpression(expression: PyExpression?): Boolean {
      return expression is PyReferenceExpression && isTypeCheckingReference(expression)
    }

    private fun isTypeCheckingReference(expression: PyReferenceExpression): Boolean {
      val qualifiedName = expression.asQualifiedName()
      // The control flow, the scopes and the lists of file and class members use this check, and name resolution uses them.
      // Thus, the check cannot resolve `t` in `t.TYPE_CHECKING` to `typing`, and it accepts any qualifier. mypy also does this.
      return qualifiedName != null && qualifiedName.componentCount <= 2 && TYPE_CHECKING == qualifiedName.lastComponent
    }
  }
}
