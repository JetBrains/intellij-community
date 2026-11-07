// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.impl.PyEvaluator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigInteger

@TestFor(classes = [PyEvaluator::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyEvaluatorTest : PyCodeInsightTestCase() {

  @Test
  fun `null expression`() = runInEdtAndWait {
    assertNull(PyEvaluator().evaluate(null))
  }

  @Test
  fun parenthesized() = runInEdtAndWait {
    assertTrue(byExpression<Boolean>("(True)"))
  }

  @Test
  fun dict() = runInEdtAndWait {
    assertEquals(mapOf("a" to 1, 2 to "b"), byExpression<Map<*, *>>("{'a': 1, 2: 'b'}"))
    assertEquals(mapOf("a" to 1, 2 to "b"), byText<Map<*, *>>("""
      a = 'a'
      one = 1
      two = 2
      b = 'b'
      expr = {a: one, two: b}
    """))
  }

  @Test
  fun list() = runInEdtAndWait {
    assertEquals(listOf(1, 3, 5), byExpression<List<*>>("[1, 3, 5]"))
    assertEquals(listOf(1, 3, 5), byText<List<*>>("""
      one = 1
      three = 3
      five = 5
      expr = [one, three, five]
    """))
  }

  @Test
  fun boolean() = runInEdtAndWait {
    assertFalse(byExpression<Boolean>("False"))
  }

  @Test
  fun string() = runInEdtAndWait {
    assertEquals("abc", byExpression<String>("'abc'"))
  }

  @Test
  fun integer() = runInEdtAndWait {
    assertEquals(5, byExpression<Int>("5"))
  }

  @Test
  fun long() = runInEdtAndWait {
    val expected = Int.MAX_VALUE.toLong() + 1
    assertEquals(expected, byExpression<Long>(expected.toString()))
  }

  @Test
  fun `big integer`() = runInEdtAndWait {
    val expected = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)
    assertEquals(expected, byExpression<BigInteger>(expected.toString()))
  }

  @Test
  fun `concatenation of strings`() = runInEdtAndWait {
    assertEquals("abcd", byExpression<String>("'ab' + 'cd'"))
  }

  @Test
  fun `concatenation of lists`() = runInEdtAndWait {
    assertEquals(listOf(1, "a", "b", 2), byExpression<List<*>>("[1, 'a'] + ['b', 2]"))
  }

  @Test
  fun reference() = runInEdtAndWait {
    assertTrue(byText<Boolean>("""
      a = True
      expr = a
    """))
  }

  @Test
  fun `reference chain`() = runInEdtAndWait {
    assertTrue(byText<Boolean>("""
      a = True
      b = a
      expr = b
    """))
  }

  @Test
  fun `string replace`() = runInEdtAndWait {
    assertEquals("bbb", byExpression<String>("'aaa'.replace('a', 'b')"))
  }

  @Test
  fun `dict from tuples`() = runInEdtAndWait {
    assertEquals(mapOf("a" to 1, 2 to "b"), byText<Map<*, *>>("expr = dict([('a', 1), (2, 'b')])"))
  }

  @Test
  fun `list items not evaluated`() = runInEdtAndWait {
    val evaluator = PyEvaluator()
    evaluator.setEvaluateCollectionItems(false)

    val list = assertInstanceOf(List::class.java, evaluator.evaluate(parseText("""
      a = 10
      b = 20
      expr = [a, b]
    """)))

    assertEquals(2, list.size)
    assertEquals(10, evaluator.evaluate(assertInstanceOf(PyReferenceExpression::class.java, list[0])))
    assertEquals(20, evaluator.evaluate(assertInstanceOf(PyReferenceExpression::class.java, list[1])))
  }

  @Test
  fun `dict keys not evaluated`() = runInEdtAndWait {
    val evaluator = PyEvaluator()
    evaluator.setEvaluateKeys(false)

    val map = assertInstanceOf(Map::class.java, evaluator.evaluate(parseText(DICT_OF_REFERENCES)))

    val keysByValue = map.entries.associate { (key, value) ->
      value to evaluator.evaluate(assertInstanceOf(PyReferenceExpression::class.java, key))
    }
    assertEquals(mapOf(1 to "a", "b" to 2), keysByValue)
  }

  @Test
  fun `dict values not evaluated`() = runInEdtAndWait {
    val evaluator = PyEvaluator()
    evaluator.setEvaluateCollectionItems(false)

    val map = assertInstanceOf(Map::class.java, evaluator.evaluate(parseText(DICT_OF_REFERENCES)))

    val valuesByKey = map.entries.associate { (key, value) ->
      key to evaluator.evaluate(assertInstanceOf(PyReferenceExpression::class.java, value))
    }
    assertEquals(mapOf("a" to 1, 2 to "b"), valuesByKey)
  }

  @Test
  fun `dict keys and values not evaluated`() = runInEdtAndWait {
    val evaluator = PyEvaluator()
    evaluator.setEvaluateKeys(false)
    evaluator.setEvaluateCollectionItems(false)

    val map = assertInstanceOf(Map::class.java, evaluator.evaluate(parseText(DICT_OF_REFERENCES)))

    val evaluated = map.entries.associate { (key, value) ->
      evaluator.evaluate(assertInstanceOf(PyReferenceExpression::class.java, key)) to
        evaluator.evaluate(assertInstanceOf(PyReferenceExpression::class.java, value))
    }
    assertEquals(mapOf("a" to 1, 2 to "b"), evaluated)
  }

  @Test
  fun `reference with namespace`() = runInEdtAndWait {
    val evaluator = PyEvaluator()
    evaluator.setNamespace(mapOf("a" to 1))

    assertEquals(1, evaluator.evaluate(parseText("""
      a = True
      expr = a
    """)))
  }

  @Test
  fun `reference with incomplete namespace`() = runInEdtAndWait {
    val evaluator = PyEvaluator()
    evaluator.setNamespace(mapOf("b" to 1))

    assertNull(evaluator.evaluate(parseText("""
      a = True
      b = a
      c = b
      expr = c
    """)))
  }

  @Test
  fun `addition of numbers`() = runInEdtAndWait {
    assertEquals(3, byExpression<Int>("1 + 2"))

    assertEquals(Int.MAX_VALUE.toLong() + 1, byExpression<Long>("1 + ${Int.MAX_VALUE}"))
    assertEquals(Int.MAX_VALUE.toLong() + 1, byExpression<Long>("${Int.MAX_VALUE} + 1"))

    val longMaxPlusOne = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)
    assertEquals(longMaxPlusOne, byExpression<BigInteger>("1 + ${Long.MAX_VALUE}"))
    assertEquals(longMaxPlusOne, byExpression<BigInteger>("${Long.MAX_VALUE} + 1"))
  }

  @Test
  fun `subtraction multiplication and floor division of numbers`() = runInEdtAndWait {
    assertEquals(17, byExpression<Int>("20 - 3"))
    assertEquals(60, byExpression<Int>("20 * 3"))
    assertEquals(6, byExpression<Int>("20 // 3"))
  }

  @Test
  fun `comparison of numbers`() = runInEdtAndWait {
    assertTrue(byExpression<Boolean>("1 < 2"))
    assertTrue(byExpression<Boolean>("1 <= 1"))
    assertTrue(byExpression<Boolean>("2 > 1"))
    assertTrue(byExpression<Boolean>("1 >= 1"))
    assertTrue(byExpression<Boolean>("1 == 1"))
    assertTrue(byExpression<Boolean>("2 != 1"))
  }

  @Test
  fun `unary plus on a number`() = runInEdtAndWait {
    assertEquals(5, byExpression<Int>("+5"))
    assertEquals(Long.MAX_VALUE, byExpression<Long>("+${Long.MAX_VALUE}"))
    val longMaxPlusOne = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)
    assertEquals(longMaxPlusOne, byExpression<BigInteger>("+$longMaxPlusOne"))
  }

  @Test
  fun `boolean operators`() = runInEdtAndWait {
    assertTrue(byExpression<Boolean>("True and True"))
    assertTrue(byExpression<Boolean>("True or False"))
    assertTrue(byExpression<Boolean>("not False"))
  }

  @Test
  fun `reference with several targets`() = runInEdtAndWait {
    assertNull(PyEvaluator().evaluate(parseText("""
      if condition:
          a = 1
      else:
          a = 3
      expr = a < 2
    """)))
  }

  @Test
  fun `evaluate as boolean`() = runInEdtAndWait {
    assertEquals(true, PyEvaluator.evaluateAsBoolean(parseExpression("True")))
    assertEquals(false, PyEvaluator.evaluateAsBoolean(parseExpression("False")))

    assertEquals(true, PyEvaluator.evaluateAsBoolean(parseExpression("'a'")))
    assertEquals(false, PyEvaluator.evaluateAsBoolean(parseExpression("''")))

    assertEquals(true, PyEvaluator.evaluateAsBoolean(parseExpression("1")))
    assertEquals(false, PyEvaluator.evaluateAsBoolean(parseExpression("0")))

    assertEquals(true, PyEvaluator.evaluateAsBoolean(parseExpression("{1: 0}")))
    assertEquals(false, PyEvaluator.evaluateAsBoolean(parseExpression("{}")))

    assertEquals(true, PyEvaluator.evaluateAsBoolean(parseExpression("[1]")))
    assertEquals(false, PyEvaluator.evaluateAsBoolean(parseExpression("[]")))
  }

  @Test
  fun `evaluate as boolean without resolve`() = runInEdtAndWait {
    assertEquals(true, PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("True")))
    assertEquals(false, PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("False")))

    assertEquals(true, PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("'a'")))
    assertEquals(false, PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("''")))

    assertEquals(true, PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("1")))
    assertEquals(false, PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("0")))

    assertEquals(true, PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("{1: 0}")))
    assertEquals(false, PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("{}")))

    assertEquals(true, PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("[1]")))
    assertEquals(false, PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("[]")))

    for (value in listOf("True", "False", "'a'", "''", "1", "0", "{1: 0}", "{}", "[1]", "[]")) {
      assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("""
        a = $value
        expr = a
      """)), value)
    }
  }

  @Test
  fun `TYPE_CHECKING from typing`() = runInEdtAndWait {
    assertEquals(true, PyEvaluator.evaluateAsBooleanNoResolve(parseText("expr = typing.TYPE_CHECKING")))
    assertEquals(true, PyEvaluator.evaluateAsBooleanNoResolve(parseText("expr = TYPE_CHECKING")))
  }

  @Test
  @TestFor(issues = ["PY-87570"])
  fun `TYPE_CHECKING from an alias of typing`() = runInEdtAndWait {
    // Name resolution uses the control flow and the scopes, and they use this check. Thus, the check cannot resolve `t` to `typing`.
    assertEquals(true, PyEvaluator.evaluateAsBooleanNoResolve(parseText("expr = t.TYPE_CHECKING")))
    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("expr = a.b.TYPE_CHECKING")))
  }

  @Test
  @TestFor(issues = ["PY-85200"])
  fun `boolean short circuit`() = runInEdtAndWait {
    // `True or <unknown>` is True even when the other operand does not evaluate, like `if TYPE_CHECKING or not USE_EXTENSIONS:`.
    assertEquals(true, PyEvaluator.evaluateAsBooleanNoResolve(parseText("expr = TYPE_CHECKING or undefined_flag")))
    assertEquals(true, PyEvaluator.evaluateAsBooleanNoResolve(parseText("expr = undefined_flag or TYPE_CHECKING")))
    assertEquals(true, PyEvaluator.evaluateAsBooleanNoResolve(parseText("expr = TYPE_CHECKING or not undefined_flag")))

    assertEquals(false, PyEvaluator.evaluateAsBooleanNoResolve(parseText("expr = not TYPE_CHECKING and undefined_flag")))
    assertEquals(false, PyEvaluator.evaluateAsBooleanNoResolve(parseText("expr = undefined_flag and not TYPE_CHECKING")))

    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("expr = TYPE_CHECKING and undefined_flag")))
    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("expr = not TYPE_CHECKING or undefined_flag")))
  }

  @Test
  fun `sys version check`() = runInEdtAndWait {
    fun check(condition: String) = PyEvaluator.evaluateAsBooleanNoResolve(parseText("expr = $condition"), LanguageLevel.PYTHON34)

    assertEquals(true, check("sys.version_info >= (3, 4)"))
    assertEquals(false, check("sys.version_info >= (3, 5)"))

    assertEquals(false, check("sys.version_info > (3, 4)"))
    assertEquals(true, check("sys.version_info > (3, 3)"))

    assertEquals(true, check("sys.version_info <= (3, 4)"))
    assertEquals(false, check("sys.version_info <= (3, 3)"))

    assertEquals(false, check("sys.version_info < (3, 4)"))
    assertEquals(true, check("sys.version_info < (3, 5)"))
  }

  @Test
  @TestFor(issues = ["PY-93851"])
  fun `version tuple with a named item gives no value`() = runInEdtAndWait {
    assertNull(PyEvaluator.evaluateAsVersion(parseText("""
      MINOR = 4
      expr = (3, MINOR)
    """)))
    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("""
      import sys
      MINOR = 4
      expr = sys.version_info >= (3, MINOR)
    """), LanguageLevel.PYTHON34))
  }

  @Test
  @TestFor(issues = ["PY-93846"])
  fun `floor division by zero gives no value`() = runInEdtAndWait {
    assertNull(PyEvaluator().evaluate(parseExpression("1 // 0")))
    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("1 // 0")))
  }

  @Test
  @TestFor(issues = ["PY-93846"])
  fun `floor division rounds down`() = runInEdtAndWait {
    assertEquals(3, byExpression<Int>("7 // 2"))
    assertEquals(-4, byExpression<Int>("-7 // 2"))
    assertEquals(-4, byExpression<Int>("7 // -2"))
    assertEquals(3, byExpression<Int>("-7 // -2"))
    assertEquals(-4, byExpression<Int>("-8 // 2"))
  }

  @Test
  @TestFor(issues = ["PY-93846"])
  fun `value of one target evaluates for each reference`() = runInEdtAndWait {
    assertEquals("aa", byText<String>("""
      x = "a"
      expr = x + x
    """))
    assertEquals(listOf(1, 1), byText<List<*>>("""
      a = 1
      expr = [a, a]
    """))
  }

  @Test
  @TestFor(issues = ["PY-93846"])
  fun `reference cycle gives no value`() = runInEdtAndWait {
    assertNull(PyEvaluator().evaluate(parseText("""
      a = b
      b = a
      expr = a
    """)))
  }

  @Test
  @TestFor(issues = ["PY-93846"])
  fun `value in a reference cycle evaluates for each reference`() = runInEdtAndWait {
    assertEquals(listOf(true, true), byText<List<*>>("""
      a = True or b
      b = a
      expr = [a, b]
    """))
  }

  @Test
  @TestFor(issues = ["PY-93846"])
  fun `f-string evaluates only without interpolation`() = runInEdtAndWait {
    assertEquals("abc", byExpression<String>("f'abc'"))
    assertNull(PyEvaluator().evaluate(parseExpression("f'a{x}'")))
    assertNull(PyEvaluator().evaluate(parseExpression("'a' f'{x}'")))
    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("f'{x}'")))
  }

  @Test
  @TestFor(issues = ["PY-93846"])
  fun `t-string gives no value`() = runInEdtAndWait {
    assertNull(PyEvaluator().evaluate(parseExpression("t'abc'")))
  }

  @Test
  @TestFor(issues = ["PY-93846"])
  fun `bytes literal gives no value`() = runInEdtAndWait {
    assertNull(PyEvaluator().evaluate(parseExpression("b'a'")))
    assertNull(PyEvaluator().evaluate(parseExpression("b'a' + 'b'")))
  }

  @Test
  @TestFor(issues = ["PY-93846"])
  fun `evaluate as boolean of a large integer`() = runInEdtAndWait {
    assertEquals(true, PyEvaluator.evaluateAsBoolean(parseExpression("3000000000")))
    assertEquals(true, PyEvaluator.evaluateAsBoolean(parseExpression("100000000000000000000")))
    assertEquals(false, PyEvaluator.evaluateAsBoolean(parseExpression("3000000000 - 3000000000")))
  }

  @Test
  @TestFor(issues = ["PY-93846"])
  fun `dict from pairs reads only the top-level pairs`() = runInEdtAndWait {
    assertEquals(mapOf("a" to listOf(1, 2)), byText<Map<*, *>>("expr = dict([('a', (1, 2))])"))
    assertEquals(mapOf("a" to 1, "b" to 2), byText<Map<*, *>>("expr = dict([('a', 1)], b=2)"))
    assertEquals(mapOf("title" to "x"), byText<Map<*, *>>("expr = dict(base, title='x')"))
    assertNull(PyEvaluator().evaluate(parseText("expr = dict([('a', 1, 2)])")))
  }

  private inline fun <reified T : Any> byExpression(expression: String): T =
    assertInstanceOf(T::class.javaObjectType, PyEvaluator().evaluate(parseExpression(expression)))

  private inline fun <reified T : Any> byText(text: String): T =
    assertInstanceOf(T::class.javaObjectType, PyEvaluator().evaluate(parseText(text)))

  private fun parseExpression(expression: String): PyExpression =
    PyElementGenerator.getInstance(myFixture.project).createExpressionFromText(LanguageLevel.getLatest(), expression)

  private fun parseText(text: String): PyExpression {
    myFixture.configureByText(PythonFileType.INSTANCE, text.trimIndent())
    val target = myFixture.findElementByText("expr", PyTargetExpression::class.java)
    assertNotNull(target)
    return target.findAssignedValue()!!
  }

  private companion object {
    const val DICT_OF_REFERENCES = """
      a = 'a'
      one = 1
      two = 2
      b = 'b'
      expr = {a: one, two: b}
    """
  }
}
