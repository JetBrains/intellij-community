// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.documentation.docstrings.DocStringFormat
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.numpy.codeInsight.NumpyDocStringTypeProvider
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Types that docstrings give: reStructuredText `:type:`/`:rtype:` fields, NumPy and Google sections, and the type
 * forms that a docstring type can use.
 */
@Subsystems.Typing
@Components.Docstrings
@Layers.Functional
class PyDocstringTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class ReStructuredText {
    @Test
    fun `rest param type`() = test("""
      def foo(limit):
          ''':param integer limit: maximum number of stack frames to show'''
          expr = limit
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-3849"])
    fun `rest class type`() = test("""
      class Foo: pass
      def foo(limit):
          ''':param :class:`Foo` limit: maximum number of stack frames to show'''
          expr = limit
      #   └ TYPE Foo
      """.trimIndent())

    @Test
    fun `rest ivar type`() = test("""
      def foo(p):
          var = p.bar
          ''':type var: str'''
          expr = var
      #   └ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-6584"])
    fun `class attribute type in class docstring via class`() = test("""
      class C(object):
          '''
          :type foo: int
          '''
          foo = None
      
      expr = C.foo
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-6584"])
    fun `class attribute type in class docstring via instance`() = test("""
      class C(object):
          '''
          :type foo: int
          '''
          foo = None
      
      expr = C().foo
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-6584"])
    fun `instance attribute type in class docstring`() = test("""
      class C(object):
          '''
          :type foo: int
          '''
          def __init__(self, bar):
              self.foo = bar
      
      def f(x):
          expr = C(x).foo
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-8953"])
    fun `self type in docstring`() = test("""
      class C(object):
          def foo(self):
              '''
              :type self: int
              '''
              expr = self
      #       └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-7322"])
    fun `namedtuple parameter type in docstring`() = test("""
      from collections import namedtuple
      Point = namedtuple('Point', ('x', 'y'))
      def takes_a_point(point):
          '''
          :type point: Point
          '''
          expr = point
      #   └ TYPE Point
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-4813"])
    fun `parameter type inference in subclass from docstring`() = test("""
      class Base:
          def test(self, param):
              '''
              :param param:
              :type param: int
              '''
              pass
      
      class Subclass(Base):
          def test(self, param):
              expr = param
      #       └ TYPE int
      """.trimIndent())

    @Test
    fun simple() = test("""
      def f1(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10=10, p11='11'):
          '''
          :type p1: integer
          :type p2: integer
          :type p3: float
          :type p4: float
          :type p5: int
          :type p6: integer
          :type p7: integer
          :type p8: int
          :type p9: int
          :type p10: int
          :type p11: string
          '''
          return p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + p10 + int(p11)

      def test():
          p7 = int('7')
          f1(1, '2', 3.0, 4, 5, int('6'), p7, p8=-8, p9='foo', p10='foo')
      #         │                                    │         ^^^^^^^^^ WARNING Expected type 'int', got 'Literal["foo"]' instead
      #         │                                    ^^^^^^^^ WARNING Expected type 'int', got 'Literal["foo"]' instead
      #         ^^^ WARNING Expected type 'int', got 'Literal["2"]' instead
      """.trimIndent())
  }

  @Nested
  inner class NumpyAndGoogle {
    @Test
    @TestFor(issues = ["PY-24923"])
    fun `empty numpy function docstring`() = test("""
      def f(param):
          ''''''
          expr = param
      #   └ TYPE Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24923"])
    fun `empty numpy class docstring`() = test("""
      class C:
          ''''''
          def __init__(self, param):
              expr = param
      #       └ TYPE Unknown
      """.trimIndent())

    @Test
    fun `no type in google docstring param annotation`() = test("""
      def f(x: int):
          '''
          Args:
              x: foo
          '''    
          expr = x
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-16987"])
    fun `unfilled type in google docstring param annotation`() = test("""
      def f(x: int):
          '''
          Args:
              x (): foo
          '''    
          expr = x
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-16987"])
    fun `no type in numpy docstring param annotation`() = test("""
      def f(x: int):
          '''
          Parameters
          ----------
          x
              foo
          '''
          expr = x
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-17010"])
    fun `annotated return type precedes docstring`() = test("""
      def func() -> int:
          '''
          Returns:
              str
          '''
      expr = func()
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-17010"])
    fun `annotated param type precedes docstring`() = test("""
      def func(x: int):
          '''
          Args:
              x (str):
          '''
          expr = x
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24067"])
    fun `async function return type in docstring`() = test("""
      async def f():
          '''
          :rtype: int
          '''
          pass
      expr = f()
      #└ TYPE CoroutineType[Unknown, Unknown, int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27518"])
    fun `async function return type in numpy docstring`() = test("""
      async def f():
          '''
          An integer.
      
          Returns
          -------
          int
              A number
          '''
          pass
      expr = f()
      #└ TYPE CoroutineType[Unknown, Unknown, int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-54879"], classes = [NumpyDocStringTypeProvider::class])
    fun `stub annotation in the same module wins over a NumPy docstring`() = withDocstringFormat(DocStringFormat.NUMPY) {
      test(
        NUMPY_RETURN_UNANNOTATED_FUNCTION + """
        c = f(1, '2')
        #   ^^^^^^^^^ TYPE int FIXME str
        """.trimIndent(),
        "aaa.pyi" to NUMPY_RETURN_STUB,
      )
    }

    @Test
    @TestFor(issues = ["PY-54879"], classes = [NumpyDocStringTypeProvider::class])
    fun `stub annotation of an imported module wins over a NumPy docstring`() = withDocstringFormat(DocStringFormat.NUMPY) {
      test(
        """
        from mod import f

        c = f(1, '2')
        #   ^^^^^^^^^ TYPE str
        """,
        "mod.py" to NUMPY_RETURN_UNANNOTATED_FUNCTION,
        "mod.pyi" to NUMPY_RETURN_STUB,
      )
    }

    @Test
    @TestFor(issues = ["PY-56612"])
    fun `Attributes section of a class docstring types an attribute`() = test("""
      class Message:
          author: int


      class Context:
          '''The context of a command.

          Attributes
          ----------
          message : Message
              The message
          '''

          def __init__(self, **attrs):
              self.message = attrs.pop("message", None)

          @property
          def author(self):
              return self.message.author
      #              ^^^^^^^^^^^^ TYPE Unknown FIXME Message
      #              ^^^^^^^^^^^^^^^^^^^ TYPE Unknown FIXME int
      """.trimIndent())
  }

  @Nested
  inner class TypeForms {
    @Test
    fun `no resolve to functions in docstring types`() = test("""
      class C(object):
          def bar(self):
              pass

      def foo(x):
          '''
          :type x: C | C.bar | foo
          '''
          expr = x
      #   └ TYPE C | Unknown
      """.trimIndent())

    @Test
    fun `parameter of function type and return value from docstring`() = test("""
      def func(f):
          '''
          :type f: (unknown) -> str
          '''
          return 1

      expr = func(foo)
      #│          ^^^ ERROR Unresolved reference 'foo'
      #└ TYPE Literal[1]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-21474"])
    fun `reassigning optional list with default value from docstring`() = test("""
      def x(things):
          '''
          :type things: None | list[str]
          '''
          expr = things if things else []
      #   └ TYPE list[str] | list[Unknown]
      """.trimIndent())
  }

}

private const val NUMPY_RETURN_STUB = "def f(a: int, b: str) -> str: ..."

private val NUMPY_RETURN_UNANNOTATED_FUNCTION = """
  def f(a, b):
      '''
      ...

      Parameters
      ----------
      a: str
      b: int

      Returns
      -------
      c: int

      '''
      return '3'
  """.trimIndent() + "\n\n\n"
