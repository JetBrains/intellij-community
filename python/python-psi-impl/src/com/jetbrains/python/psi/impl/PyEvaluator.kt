// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

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
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyElementType
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFormattedStringElement
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyNumericLiteralExpression
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySequenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
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
      else -> null
    }
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

    val lhs = evaluate(expression.leftExpression)
    val rhs = evaluate(expression.rightExpression)
    if (op === PyTokenTypes.PLUS && lhs != null && rhs != null) {
      return applyPlus(lhs, rhs)
    }
    if (op == null) {
      return null
    }
    return fromConstant(PyConstantFolder.binary(op, toConstant(lhs), toConstant(rhs)))
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

    return expression.elements.map { element -> if (myEvaluateCollectionItems) evaluate(element) else element }
  }

  open fun applyPlus(lhs: Any?, rhs: Any?): Any? {
    if (lhs is List<*> && rhs is List<*>) {
      return ContainerUtil.concat(lhs, rhs)
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

  protected open fun evaluateCall(expression: PyCallExpression): Any? {
    val args = expression.arguments
    if (expression.isCalleeText(PyNames.REPLACE) && args.size == 2) {
      val callee = expression.callee
      if (callee is PyQualifiedExpression) {
        val evaluatedQualifier = evaluate(callee.qualifier)
        if (evaluatedQualifier is String) {
          val oldSubstring = evaluate(args[0])
          val newSubstring = evaluate(args[1])
          if (oldSubstring is String && newSubstring is String) {
            return evaluatedQualifier.replace(oldSubstring, newSubstring)
          }
        }
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

    /**
     * Returns `null` for a bytes literal, a t-string and an f-string with an interpolated fragment. Their values are not a `str`
     * that the literal text gives.
     */
    private fun evaluateString(expression: PyStringLiteralExpression): String? {
      for (element in expression.stringElements) {
        if (element.isBytes || element.isTemplate) {
          return null
        }
        if (element is PyFormattedStringElement && element.fragments.isNotEmpty()) {
          return null
        }
      }
      return expression.stringValue
    }

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
      return when (value) {
        is String -> PyConstantValue.Str(value)
        is Boolean -> PyConstantValue.Bool(value)
        is Int -> PyConstantValue.Int(BigInteger.valueOf(value.toLong()))
        is Long -> PyConstantValue.Int(BigInteger.valueOf(value))
        is BigInteger -> PyConstantValue.Int(value)
        else -> null
      }
    }

    private fun fromConstant(value: PyConstantValue?): Any? {
      return when (value) {
        null -> null
        is PyConstantValue.Str -> value.value
        is PyConstantValue.Bool -> value.value
        is PyConstantValue.Int -> fromBigInteger(value.value)
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
      return when (val result = evaluator.evaluate(expression)) {
        is Collection<*> -> !result.isEmpty()
        is Map<*, *> -> !result.isEmpty()
        else -> toConstant(result)?.let(PyConstantFolder::isTruthy)
      }
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
