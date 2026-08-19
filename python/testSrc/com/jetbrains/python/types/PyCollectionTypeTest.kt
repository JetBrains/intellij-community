// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Components
import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for collections and subscription: list/dict/set literals,
 * builtin-generic parameterization, indexing/`__getitem__`, slicing of list/str/bytes and
 * item lookup.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyCollectionTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class ListSetDictLiterals {

    @Test
    fun `list literal types`() = test("""
      expr = []
      # └ TYPE list[Unknown]
      """.trimIndent())

    @Test
    fun `list literal of ints`() = test("""
      expr = [1, 2, 3]
      # └ TYPE list[int]
      """.trimIndent())

    @Test
    fun `list literal of mixed values`() = test("""
      expr = ['1', 1, 1]
      # └ TYPE list[str | int]
      """.trimIndent())

    @Test
    fun `list literal of many mixed values`() = test("""
      expr = ['1', 1, 1, 1, 1, 1, 1, 1, 1, 1, 1]
      #└ TYPE list[str | int]
      """.trimIndent())

    @Test
    fun `set literal of ints`() = test("""
      expr = {1}
      #└ TYPE set[int]
      """.trimIndent())

    @Test
    fun `set literal of mixed values`() = test("""
      expr = {'1', 1, 1}
      # └ TYPE set[str | int]
      """.trimIndent())

    @Test
    fun `set literal of many mixed values`() = test("""
      expr = {'1', 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
      #└ TYPE set[str | int]
      """.trimIndent())

    @Test
    fun `empty dict literal`() = test("""
      expr = {}
      #└ TYPE dict[Unknown, Unknown]
      """.trimIndent())

    @Test
    fun `dict literal of int to bool`() = test("""
      expr = {1: False}
      #└ TYPE dict[int, bool]
      """.trimIndent())

    @Test
    fun `dict literal of mixed keys and values`() = test("""
      expr = {'1': 1, 1: '1', 1: 1}
      #└ TYPE dict[str | int, int | str]
      """.trimIndent())

    @Test
    fun `dict literal of many mixed keys and values`() = test("""
      expr = {'1': 1, 1: '1', 1: 1, 1: 1, 1: 1, 1: 1, 1: 1, 1: 1, 1: 1, 1: 1, 1: 1}
      #└ TYPE dict[str | int | Unknown, int | str | Unknown]
      """.trimIndent())
  }

  @Nested
  inner class IndexingAndSlicing {

    @Test
    fun `list item by integer index`() = test("""
      l = [1, 2, 3]; expr = l[0]
      #              └ TYPE int
      """.trimIndent())

    @Test
    fun `list slice type`() = test("""
      l = [1, 2, 3]; expr = l[0:1]
      #              └ TYPE list[int]
      """.trimIndent())

    @Test
    fun `subscription expression`() = test("""
      def test():
          def f(x):
              '''
              :type x: str
              '''
          class C(object):
              def __getitem__(self, item):
                  '''
                  :type item: str
                  :rtype: int
                  '''
          xs = [1, 2, 3]
          x = xs[0]
          f(x)
      #     └ WARNING Expected type 'str', got 'int' instead
          c = C()
          c_0 = c[0]
      #           └ WARNING Expected type 'str', got 'Literal[0]' instead
          f(c_0)
      #     ^^^ WARNING Expected type 'str', got 'int' instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-5474"])
    fun `bad subscript expression`() = test("""
      def test():
          x = r'''\x''
          r'''[\t\r\v]'''
      #        │││││││^^^^^^^^^^^^^^^^^^^^ ERROR Missing closing triple quotes
      #        │││││││└ ERROR Statement expected, found Py:RBRACKET
      #        ││││││└ ERROR End of statement expected
      #        │││││└ ERROR Statement expected, found Py:BACKSLASH
      #        │││││└ ERROR Unresolved reference 'v'
      #        ││││└ ERROR End of statement expected
      #        │││└ ERROR Statement expected, found Py:BACKSLASH
      #        │││└ ERROR Unresolved reference 'r'
      #        ││└ ERROR End of statement expected
      #        │└ ERROR Statement expected, found Py:BACKSLASH
      #        │└ ERROR Unresolved reference 't'
      #        └ ERROR Expression expected
      """.trimIndent())

  }

  @Nested
  inner class BuiltinGenericParameterization {

    @Test
    fun `builtin list from typing List`() = test("""
      from typing import List

      def f(expr: List):
      #     └ TYPE list
          pass
      """.trimIndent())

    @Test
    fun `builtin list with parameter`() = test("""
      from typing import List

      def f(expr: List[int]):
      #     └ TYPE list[int]
          pass
      """.trimIndent())

    @Test
    fun `builtin dict with parameters`() = test("""
      from typing import Dict

      def f(expr: Dict[str, int]):
      #     └ TYPE dict[str, int]
          pass
      """.trimIndent())
  }

  @Nested
  inner class ListItemAndSublistLookup {

    @Test
    @TestFor(issues = ["PY-19858"])
    fun `get list item by integral`() = test("""
      from typing import List

      def foo(x: List[List]):
          expr = x[0]
      #   └ TYPE list
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-19858"])
    fun `get list item by indirect integral`() = test("""
      from typing import List

      def foo(x: List[List]):
          y = 0
          expr = x[y]
      #   └ TYPE list
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-19858"])
    fun `get sublist by slice`() = test("""
      from typing import List

      def foo(x: List[List]):
          expr = x[1:3]
      #   └ TYPE list[list]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-19858"])
    fun `get sublist by indirect slice`() = test("""
      from typing import List

      def foo(x: List[List]):
          y = slice(1, 3)
          expr = x[y]
      #   └ TYPE list[list]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-19858"])
    fun `get list item by unknown`() = test("""
      from typing import List

      def foo(x: List[List]):
          expr = x[y]
      #   │        └ ERROR Unresolved reference 'y'
      #   └ TYPE UnsafeUnion[list, list[list]]
      """.trimIndent())

    @Test
    fun `get list of lists item by integral`() = test("""
      from typing import List

      def foo(x: List[List]):
          sublist = x[0]
          expr = sublist[0]
      #   └ TYPE Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27627"])
    fun `item lookup not resolved as parametrized class instance`() = test("""
      d = {
          int: lambda: ()
      }
      expr = d[int]()
      #└ TYPE tuple[()]
      """.trimIndent())

    @Test
    fun `list containing classes pop`() = test("""
      xs = [str]
      expr = xs.pop()
      #└ TYPE type[str]
      """.trimIndent())
  }

  @Nested
  inner class DictValuesIteration {

    @Test
    fun `dict values type`() = test("""
      d = {'foo': 42}
      for expr in d.values():
      #   └ TYPE int
          pass
      """.trimIndent())
  }

  @Nested
  inner class SlicingItemLookup {

    @Test
    @TestFor(issues = ["PY-9924"])
    fun `list getitem with slice produces no type error`() = test("""
      l = [1, 2, 3, 4]
      s = slice(0, 2)

      x = l[s]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-20460"])
    fun `string getitem with slice produces no type error`() = test("""
      sl = slice(0, 2)
      st = "abcdef"

      print(st[sl])
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-20460"])
    fun `bytes getitem with slice produces no type error`() = test("""
      sl = slice(0, 2)
      st = b"abcdef"

      print(st[sl])
      """.trimIndent())
  }

  @Test
  @TestFor(issues = ["PY-6570"])
  fun `dict literal element type from indexing with unknown value`() = test("""
    def test(k1, v1):
        d = {k1: v1}
        return 1 + d[k1]
    """.trimIndent())

  /**
   * mirrors of tests in this suite but with PyAnyType disabled to ensure that the old style doesn't regress
   */
  @Nested
  inner class PyAnyTypeMigrationMirrors {

    @Test
    @TestCaseOptions(enablePyAnyType = false)
    fun `empty dict literal (old py-any)`() = test("""
      expr = {}
      # └ TYPE dict[Any, Any]
      """.trimIndent())
  }
}
