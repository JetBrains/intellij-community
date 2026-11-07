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
import com.jetbrains.python.psi.impl.PyConstantFolder
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

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `int operators follow Python`() = runInEdtAndWait {
    assertEvaluates("7 % 3", 1)
    assertEvaluates("-7 % 3", 2)
    assertEvaluates("7 % -3", -2)
    assertEvaluates("7 % 0", null)
    assertEvaluates("2 ** 10", 1024)
    assertEvaluates("(-2) ** 3", -8)
    assertEvaluates("2 ** 100", BigInteger("1267650600228229401496703205376"))
    assertEvaluates("2 ** -1", null)
    assertEvaluates("6 & 3", 2)
    assertEvaluates("6 | 3", 7)
    assertEvaluates("6 ^ 3", 5)
    assertEvaluates("1 << 10", 1024)
    assertEvaluates("-16 >> 2", -4)
    assertEvaluates("1 >> 100", 0)
    assertEvaluates("-1 >> 100", -1)
    assertEvaluates("1 << -1", null)
    assertEvaluates("~5", -6)
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `bool acts as int in arithmetic`() = runInEdtAndWait {
    assertEvaluates("True + 1", 2)
    assertEvaluates("-True", -1)
    assertEvaluates("True & False", false)
    assertEvaluates("True | 1", 1)
    assertEvaluates("1 == True", true)
    assertEvaluates("'ab' * True", "ab")
    assertEvaluates("~True", null)
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `str repetition and comparison`() = runInEdtAndWait {
    assertEvaluates("'ab' * 3", "ababab")
    assertEvaluates("3 * 'ab'", "ababab")
    assertEvaluates("'ab' * -1", "")
    assertEvaluates("'a' < 'b'", true)
    assertEvaluates("'abc' < 'abd'", true)
    assertEvaluates("""'\U0001F600' < '￿'""", false)
    assertEvaluates("'a' < 1", null)
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `equality across kinds and of sequences`() = runInEdtAndWait {
    assertEvaluates("'a' == 1", false)
    assertEvaluates("'a' != 1", true)
    assertEvaluates("[1, 2] == [1, 2]", true)
    assertEvaluates("(1, 2) == (1, 3)", false)
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun membership() = runInEdtAndWait {
    assertEvaluates("'b' in 'abc'", true)
    assertEvaluates("'d' not in 'abc'", true)
    assertEvaluates("2 in (1, 2)", true)
    assertEvaluates("3 in [1, 2]", false)
    assertEvaluates("1 in 'abc'", null)
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `and and or return an operand`() = runInEdtAndWait {
    assertEvaluates("0 or 'a'", "a")
    assertEvaluates("'' and 1", "")
    assertEvaluates("1 and 2", 2)
    assertEvaluates("not 0", true)
    assertEvaluates("not 'a'", false)
    assertEvaluates("not []", true)
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `conditional expression`() = runInEdtAndWait {
    assertEvaluates("'a' if 1 else 'b'", "a")
    assertEvaluates("'a' if '' else 'b'", "b")
    assertEvaluates("'a' if undefined else 'b'", null)
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `subscript and slice count code points`() = runInEdtAndWait {
    assertEvaluates("'abc'[1]", "b")
    assertEvaluates("'abc'[-1]", "c")
    assertEvaluates("'abc'[3]", null)
    assertEvaluates("'abcdef'[1:4]", "bcd")
    assertEvaluates("'abcdef'[::-1]", "fedcba")
    assertEvaluates("'abcdef'[::2]", "ace")
    assertEvaluates("'abcdef'[-2:]", "ef")
    assertEvaluates("'abcdef'[5:1:-2]", "fd")
    assertEvaluates("'abc'[::0]", null)
    assertEvaluates("(1, 2, 3)[1]", 2)
    assertEvaluates("[1, 2, 3][1:]", listOf(2, 3))
    assertEvaluates("""'a\U0001F600b'[1]""", "😀")
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `str methods follow Python`() = runInEdtAndWait {
    assertEvaluates("'asdf'.upper()", "ASDF")
    assertEvaluates("'Straße'.upper()", "STRASSE")
    assertEvaluates("'ABC'.lower()", "abc")
    assertEvaluates("'ABC'.casefold()", "abc")
    assertEvaluates(""""they're bill's".title()""", "They'Re Bill'S")
    assertEvaluates("'hELLO wORLD'.capitalize()", "Hello world")
    assertEvaluates("'  a b  '.strip()", "a b")
    assertEvaluates("""'\xa0a\xa0'.strip()""", "a")
    assertEvaluates("'xxaxx'.strip('x')", "a")
    assertEvaluates("'  a'.lstrip()", "a")
    assertEvaluates("'a  '.rstrip()", "a")
    assertEvaluates("' a  b c '.split()", listOf("a", "b", "c"))
    assertEvaluates("'a,b,,c'.split(',')", listOf("a", "b", "", "c"))
    assertEvaluates("'a,b,c'.split(',', 1)", listOf("a", "b,c"))
    assertEvaluates("'-'.join(['a', 'b'])", "a-b")
    assertEvaluates("'abc'.startswith('ab')", true)
    assertEvaluates("'abc'.startswith(('x', 'a'))", true)
    assertEvaluates("'abc'.endswith('bc')", true)
    assertEvaluates("'prefix_x'.removeprefix('prefix_')", "x")
    assertEvaluates("'x.py'.removesuffix('.py')", "x")
    assertEvaluates("'42'.zfill(5)", "00042")
    assertEvaluates("'-42'.zfill(5)", "-0042")
    assertEvaluates("'abc'.zfill(2)", "abc")
    assertEvaluates("'aaa'.replace('a', 'b', 2)", "bba")
    assertEvaluates("'ab'.replace('', '-')", "-a-b-")
    assertEvaluates("'ab'.replace('', '-', 1)", "-ab")
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `str methods give no value where Python raises or the folder does not know the result`() = runInEdtAndWait {
    assertEvaluates("'a'.split('')", null)
    assertEvaluates("'-'.join(('a', 1))", null)
    assertEvaluates("'Straße'.casefold()", null)
    assertEvaluates("'a,b'.split(sep=',')", null)
    assertEvaluates("'abc'.find('b')", null)
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `str method needs no resolve`() = runInEdtAndWait {
    assertEquals("ASDF", PyEvaluator.evaluateNoResolve(parseExpression("'asdf'.upper()"), String::class.java))
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `builtins follow Python`() = runInEdtAndWait {
    assertEquals(3, PyEvaluator().evaluate(parseText("""expr = len('a\U0001F600b')""")))
    assertEquals(2, PyEvaluator().evaluate(parseText("expr = len([1, 2])")))
    assertEquals("10", PyEvaluator().evaluate(parseText("expr = str(10)")))
    assertEquals("True", PyEvaluator().evaluate(parseText("expr = str(True)")))
    assertEquals(1000, PyEvaluator().evaluate(parseText("expr = int(' 1_000 ')")))
    assertNull(PyEvaluator().evaluate(parseText("expr = int('1__0')")))
    assertEquals(1, PyEvaluator().evaluate(parseText("expr = int(True)")))
    assertEquals(false, PyEvaluator().evaluate(parseText("expr = bool('')")))
    assertEquals(128512, PyEvaluator().evaluate(parseText("""expr = ord('\U0001F600')""")))
    assertEquals("A", PyEvaluator().evaluate(parseText("expr = chr(65)")))
    assertNull(PyEvaluator().evaluate(parseText("expr = chr(-1)")))
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `builtin needs resolve to builtins`() = runInEdtAndWait {
    assertNull(PyEvaluator.evaluateNoResolve(parseText("expr = len('abc')"), Any::class.java))
    assertNull(PyEvaluator().evaluate(parseText("""
      def len(x):
          return 0
      expr = len('abc')
    """)))
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `f-string folds its fragments`() = runInEdtAndWait {
    assertEvaluates("f'{1 + 1}x'", "2x")
    assertEvaluates("'a' f'{2}'", "a2")
    assertEvaluates("f'{True!s}'", "True")
    assertEvaluates("f'{{}}'", "{}")
    assertEvaluates("f'{1!r}'", null)
    assertEvaluates("f'{1:>3}'", null)
    assertEvaluates("f'{1=}'", null)
    assertEquals("a!", PyEvaluator().evaluate(parseText("""
      x = 'a'
      expr = f'{x}!'
    """)))
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `result over the limits gives no value`() = runInEdtAndWait {
    assertEvaluates("2 ** 100000", null)
    assertEvaluates("1 << 100000", null)
    assertEvaluates("'a' * 100000", null)
    assertEvaluates("'0'.zfill(100000)", null)
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `chained comparison is a conjunction of its links`() = runInEdtAndWait {
    assertEvaluates("3 > 2 > 1", true)
    assertEvaluates("2 == 2 == 2", true)
    assertEvaluates("0 == 0 == 1", false)
    assertEvaluates("1 in [1] in [[1]]", true)
    assertEvaluates("(3 > 2) > 1", false)
    assertEvaluates("3 < 2 < undefined", false)
    assertEvaluates("1 < undefined < 3", null)
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `tuple list and set stay apart`() = runInEdtAndWait {
    assertEvaluates("[1] == (1,)", false)
    assertEvaluates("(1,) + (2,)", listOf(1, 2))
    assertEvaluates("{1, 2}[0]", null)
    assertEvaluates("'a'.startswith(['a'])", null)
    assertEvaluates("''.join({'b', 'a'})", null)
    assertNull(PyEvaluator().evaluate(parseText("expr = len({1, 1})")))
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `or with an unknown left operand gives no value but a known truth`() = runInEdtAndWait {
    assertEvaluates("undefined or True", null)
    assertEvaluates("undefined and False", null)
    assertEquals(true, PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("undefined or True")))
    assertEquals(false, PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("undefined and False")))
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `int accepts the whitespace of Python int`() = runInEdtAndWait {
    assertNull(PyEvaluator().evaluate(parseText("""expr = int('\x1c1')""")))
    assertEquals(1, PyEvaluator().evaluate(parseText("""expr = int('\xa01')""")))
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `literal that Java decodes unlike Python gives no value`() = runInEdtAndWait {
    assertEvaluates("'\\ud83d\\ude00'", null)
    assertNull(PyEvaluator().evaluate(parseText("expr = chr(0xD83D)")))
  }

  @Test
  @TestFor(issues = ["PY-93852"])
  fun `escapes fold as Python decodes them`() = runInEdtAndWait {
    assertEvaluates("""'\N{BULLET}'""", "\u2022")
    assertEvaluates("""f'\N{BULLET}{1}'""", "\u20221")
    assertEvaluates("'a\\\nb'", "ab")
    assertEvaluates("'\\a'", Char(7).toString())
    assertEvaluates("r'\\u0041'", "\\u0041")
    assertEvaluates("""'\\N{BULLET}'""", "\\N{BULLET}")
    assertEvaluates("""r'C:\New'""", "C:\\New")
    assertEvaluates("""rf'C:\New{1}'""", "C:\\New1")
  }

  @Test
  @TestFor(issues = ["PY-93852"])
  fun `named escape that the decoder keeps as text gives no value`() = runInEdtAndWait {
    assertEvaluates("""'\N{NBSP}'""", null)
    assertEvaluates("""'\N{HANGUL SYLLABLE GA}'""", null)
    assertEvaluates("""f'\N{NBSP}{1}'""", null)
    assertEvaluates("""'\N'""", null)
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `plain literal over the limit keeps its value`() = runInEdtAndWait {
    val text = "a".repeat(PyConstantFolder.MAX_STRING_LENGTH + 1)
    assertEvaluates("'$text'", text)
  }

  @Test
  @TestFor(issues = ["PY-93848"])
  fun `nested tuples that share items stay within the budget`() = runInEdtAndWait {
    val source = buildString {
      appendLine("x0 = (1, 2)")
      for (i in 1..40) {
        appendLine("x$i = (x${i - 1}, x${i - 1})")
      }
      appendLine("expr = x40[0]")
    }
    assertNull(PyEvaluator().evaluate(parseText(source)))
  }

  private fun assertEvaluates(expression: String, expected: Any?) {
    assertEquals(expected, PyEvaluator().evaluate(parseExpression(expression)), expression)
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
