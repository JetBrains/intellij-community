// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.google.common.collect.ImmutableMap;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyEvaluator;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PyEvaluatorTest extends PyTestCase {

  public void testNull() {
    assertNull(new PyEvaluator().evaluate(null));
  }

  public void testParenthesized() {
    assertTrue(byExpression("(True)", Boolean.class));
  }

  public void testDict() {
    final Map map1 = byExpression("{'a': 1, 2: 'b'}", Map.class);
    assertEquals(ImmutableMap.of("a", 1, 2, "b"), map1);

    final Map map2 = byText("a='a'\none=1\ntwo=2\nb='b'\nexpr={a: one, two: b}", Map.class);
    assertEquals(ImmutableMap.of("a", 1, 2, "b"), map2);
  }

  public void testList() {
    final List list1 = byExpression("[1, 3, 5]", List.class);
    assertEquals(Arrays.asList(1, 3, 5), list1);

    final List list2 = byText("one=1\nthree=3\nfive=5\nexpr=[one, three, five]", List.class);
    assertEquals(Arrays.asList(1, 3, 5), list2);
  }

  public void testBoolean() {
    assertFalse(byExpression("False", Boolean.class));
  }

  public void testString() {
    final String s = byExpression("'abc'", String.class);
    assertEquals("abc", s);
  }

  public void testInteger() {
    final int i = byExpression("5", Integer.class);
    assertEquals(5, i);
  }

  public void testLong() {
    final long expected = Long.valueOf(Integer.MAX_VALUE) + 1;
    final long l = byExpression(Long.toString(expected), Long.class);
    assertEquals(expected, l);
  }

  public void testBigInteger() {
    final BigInteger expected = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
    final BigInteger bigInteger = byExpression(expected.toString(), BigInteger.class);
    assertEquals(expected, bigInteger);
  }

  public void testConcatStrings() {
    final String s = byExpression("'ab' + 'cd'", String.class);
    assertEquals("abcd", s);
  }

  public void testConcatLists() {
    final List list = byExpression("[1, 'a'] + ['b', 2]", List.class);
    assertEquals(Arrays.asList(1, "a", "b", 2), list);
  }

  public void testReference() {
    assertTrue(byText("a = True\nexpr = a", Boolean.class));
  }

  public void testReferenceChain() {
    assertTrue(byText("a = True\nb = a\nexpr = b", Boolean.class));
  }

  public void testStringReplace() {
    final String s = byExpression("'aaa'.replace('a', 'b')", String.class);
    assertEquals("bbb", s);
  }

  public void testDictFromTuples() {
    final Map map = byText("expr = dict([('a', 1), (2, 'b')])", Map.class);
    assertEquals(ImmutableMap.of("a", 1, 2, "b"), map);
  }

  public void testNotEvaluatedList() {
    final PyEvaluator evaluator = new PyEvaluator();
    evaluator.setEvaluateCollectionItems(false);

    final List list = PyUtil.as(evaluator.evaluate(parseText("a = 10\nb = 20\nexpr = [a, b]")), List.class);
    assertNotNull(list);

    assertEquals(2, list.size());

    final PyReferenceExpression first = PyUtil.as(list.get(0), PyReferenceExpression.class);
    assertNotNull(first);
    assertEquals(10, evaluator.evaluate(first));

    final PyReferenceExpression second = PyUtil.as(list.get(1), PyReferenceExpression.class);
    assertNotNull(second);
    assertEquals(20, evaluator.evaluate(second));
  }

  public void testNotEvaluatedDictKeys() {
    final PyEvaluator evaluator = new PyEvaluator();
    evaluator.setEvaluateKeys(false);

    final Map map = PyUtil.as(evaluator.evaluate(parseText("a='a'\none=1\ntwo=2\nb='b'\nexpr={a: one, two: b}")), Map.class);
    assertNotNull(map);

    assertEquals(2, map.size());
    final AtomicInteger checks = new AtomicInteger();

    map.forEach(
      (k, v) -> {
        if (v instanceof Integer) {
          assertEquals(1, v);
          assertInstanceOf(k, PyReferenceExpression.class);
          assertEquals("a", evaluator.evaluate((PyReferenceExpression)k));
          checks.incrementAndGet();
        }
        else if (v instanceof String) {
          assertEquals("b", v);
          assertInstanceOf(k, PyReferenceExpression.class);
          assertEquals(2, evaluator.evaluate((PyReferenceExpression)k));
          checks.incrementAndGet();
        }
      }
    );

    assertEquals(2, checks.get());
  }

  public void testNotEvaluatedDictValues() {
    final PyEvaluator evaluator = new PyEvaluator();
    evaluator.setEvaluateCollectionItems(false);

    final Map map = PyUtil.as(evaluator.evaluate(parseText("a='a'\none=1\ntwo=2\nb='b'\nexpr={a: one, two: b}")), Map.class);
    assertNotNull(map);

    assertEquals(2, map.size());
    final AtomicInteger checks = new AtomicInteger();

    map.forEach(
      (k, v) -> {
        if (k instanceof String) {
          assertEquals("a", k);
          assertInstanceOf(v, PyReferenceExpression.class);
          assertEquals(1, evaluator.evaluate((PyReferenceExpression)v));
          checks.incrementAndGet();
        }
        else if (k instanceof Integer) {
          assertEquals(2, k);
          assertInstanceOf(v, PyReferenceExpression.class);
          assertEquals("b", evaluator.evaluate((PyReferenceExpression)v));
          checks.incrementAndGet();
        }
      }
    );

    assertEquals(2, checks.get());
  }

  public void testNotEvaluatedDictKeysAndValues() {
    final PyEvaluator evaluator = new PyEvaluator();
    evaluator.setEvaluateKeys(false);
    evaluator.setEvaluateCollectionItems(false);

    final Map map = PyUtil.as(evaluator.evaluate(parseText("a='a'\none=1\ntwo=2\nb='b'\nexpr={a: one, two: b}")), Map.class);
    assertNotNull(map);

    assertEquals(2, map.size());
    final AtomicInteger checks = new AtomicInteger();

    map.forEach(
      (k, v) -> {
        assertInstanceOf(k, PyReferenceExpression.class);
        assertInstanceOf(v, PyReferenceExpression.class);

        final Object evaluatedK = evaluator.evaluate((PyReferenceExpression)k);
        final Object evaluatedV = evaluator.evaluate((PyReferenceExpression)v);

        if (evaluatedK instanceof String) {
          assertInstanceOf(evaluatedV, Integer.class);
          assertEquals("a", evaluatedK);
          assertEquals(1, evaluatedV);
          checks.incrementAndGet();
        }
        else if (evaluatedK instanceof Integer) {
          assertInstanceOf(evaluatedV, String.class);
          assertEquals(2, evaluatedK);
          assertEquals("b", evaluatedV);
          checks.incrementAndGet();
        }
      }
    );

    assertEquals(2, checks.get());
  }

  public void testReferenceWithNamespace() {
    final PyEvaluator evaluator = new PyEvaluator();
    evaluator.setNamespace(ImmutableMap.of("a", 1));

    final Object value = evaluator.evaluate(parseText("a = True\nexpr = a"));
    assertInstanceOf(value, Integer.class);
    assertEquals(1, value);
  }

  public void testReferenceWithIncompleteNamespace() {
    final PyEvaluator evaluator = new PyEvaluator();
    evaluator.setNamespace(ImmutableMap.of("b", 1));

    assertNull(evaluator.evaluate(parseText("a = True\nb = a\nc = b\nexpr = c")));
  }

  public void testNumbersAddition() {
    assertEquals(Integer.valueOf(3), byExpression("1 + 2", Integer.class));

    assertEquals(Long.valueOf(Long.valueOf(Integer.MAX_VALUE) + 1), byExpression("1 + " + Integer.MAX_VALUE, Long.class));
    assertEquals(Long.valueOf(Long.valueOf(Integer.MAX_VALUE) + 1), byExpression(Integer.MAX_VALUE + "+ 1", Long.class));

    assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), byExpression("1 + " + Long.MAX_VALUE, BigInteger.class));
    assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), byExpression(Long.MAX_VALUE + "+ 1", BigInteger.class));
  }

  public void testNumbersSubMulDiv() {
    assertEquals(Integer.valueOf(17), byExpression("20 - 3", Integer.class));
    assertEquals(Integer.valueOf(60), byExpression("20 * 3", Integer.class));
    assertEquals(Integer.valueOf(6), byExpression("20 // 3", Integer.class));
  }

  public void testNumbersComparison() {
    assertTrue(byExpression("1 < 2", Boolean.class));
    assertTrue(byExpression("1 <= 1", Boolean.class));
    assertTrue(byExpression("2 > 1", Boolean.class));
    assertTrue(byExpression("1 >= 1", Boolean.class));
    assertTrue(byExpression("1 == 1", Boolean.class));
    assertTrue(byExpression("2 != 1", Boolean.class));
  }

  public void testBooleanOperators() {
    assertTrue(byExpression("True and True", Boolean.class));
    assertTrue(byExpression("True or False", Boolean.class));
    assertTrue(byExpression("not False", Boolean.class));
  }

  public void testMultiResolve() {
    final PyExpression expression = parseText("if condition:\n" +
                                              "    a = 1\n" +
                                              "else:\n" +
                                              "    a = 3\n" +
                                              "expr = a < 2");
    assertNull(new PyEvaluator().evaluate(expression));
  }

  public void testEvaluateAsBoolean() {
    assertTrue(PyEvaluator.evaluateAsBoolean(parseExpression("True")));
    assertFalse(PyEvaluator.evaluateAsBoolean(parseExpression("False")));

    assertTrue(PyEvaluator.evaluateAsBoolean(parseExpression("\'a\'")));
    assertFalse(PyEvaluator.evaluateAsBoolean(parseExpression("\'\'")));

    assertTrue(PyEvaluator.evaluateAsBoolean(parseExpression("1")));
    assertFalse(PyEvaluator.evaluateAsBoolean(parseExpression("0")));

    assertTrue(PyEvaluator.evaluateAsBoolean(parseExpression("{1: 0}")));
    assertFalse(PyEvaluator.evaluateAsBoolean(parseExpression("{}")));

    assertTrue(PyEvaluator.evaluateAsBoolean(parseExpression("[1]")));
    assertFalse(PyEvaluator.evaluateAsBoolean(parseExpression("[]")));
  }

  public void testEvaluateAsBooleanNoResolve() {
    assertTrue(PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("True")));
    assertFalse(PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("False")));

    assertTrue(PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("\'a\'")));
    assertFalse(PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("\'\'")));

    assertTrue(PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("1")));
    assertFalse(PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("0")));

    assertTrue(PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("{1: 0}")));
    assertFalse(PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("{}")));

    assertTrue(PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("[1]")));
    assertFalse(PyEvaluator.evaluateAsBooleanNoResolve(parseExpression("[]")));

    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("a = True\nexpr = a")));
    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("a = False\nexpr = a")));

    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("a = \'a\'\nexpr = a")));
    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("a = \'\'\nexpr = a")));

    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("a = 1\nexpr = a")));
    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("a = 0\nexpr = a")));

    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("a = {1: 0}\nexpr = a")));
    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("a = {}\nexpr = a")));

    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("a = [1]\nexpr = a")));
    assertNull(PyEvaluator.evaluateAsBooleanNoResolve(parseText("a = []\nexpr = a")));
  }

  @NotNull
  private <T> T byExpression(@NotNull String expression, @NotNull Class<T> cls) {
    final Object value = new PyEvaluator().evaluate(parseExpression(expression));
    assertInstanceOf(value, cls);
    return cls.cast(value);
  }

  @NotNull
  private PyExpression parseExpression(@NotNull String expression) {
    return PyElementGenerator.getInstance(myFixture.getProject()).createExpressionFromText(LanguageLevel.PYTHON37, expression);
  }

  @NotNull
  private <T> T byText(@NotNull String text, @NotNull Class<T> cls) {
    final Object value = new PyEvaluator().evaluate(parseText(text));
    assertInstanceOf(value, cls);
    return cls.cast(value);
  }

  @NotNull
  private PyExpression parseText(@NotNull String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    final PyTargetExpression target = myFixture.findElementByText("expr", PyTargetExpression.class);
    assertNotNull(target);
    return target.findAssignedValue();
  }
}
