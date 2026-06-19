// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.PyCallingNonCallableInspection
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type inference tests for `# type:` comments: function type comments (signature, parameter and
 * same-line variants), variable type comments, multi-assignment comments, type-comment tuples and
 * nested tuples in assignment/for/with statements, and structural-mismatch fallbacks.
 */
class PyTypeCommentTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class VariableAndAssignmentTypeComments {
    @Test
    fun `variable type comment`() = test("""
      def foo(x):
          expr = x  # type: int
      #   └ TYPE int
      """)

    @Test
    fun `multi assignment type comment`() = test("""
      def foo(x):
          c1, c2 = x  # type: int, str
          expr = c1, c2
      #   └ TYPE tuple[int, str]
      """)

    @Test
    @TestFor(issues = ["PY-19220"])
    fun `multi line assignment type comment`() = test("""
      expr = [
      #      └ WARNING Expected type 'list[str]', got 'list[Literal[1, 2]]' instead
          1,
          2,
      ]  # type: list[str]
      expr
      # └ TYPE list[str]
      """)
  }

  @Nested
  inner class ForAndWithTypeComments {
    @Test
    fun `for loop type comment`() = test("""
      def foo(xs):
          for expr, x in xs:  # type: int, str
      #       └ TYPE int
              pass
      """)

    @Test
    fun `with type comment`() = test("""
      def foo(x):
          with x as expr:  # type: int
      #        └ TYPE Unknown
              pass
      """)
  }

  @Nested
  inner class TuplesAndNestedTuples {
    @Test
    @TestFor(issues = ["PY-21191"])
    fun `type comment with parenthesized tuple`() = test("""
      expr, x = undefined()  # type: (int, str) 
      # │       ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      # └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-21191"])
    fun `type comment with nested tuples in assignment`() = test("""
      _, (_, expr) = undefined()  # type: str, (str, int)
      #         │    ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #         └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-21191"])
    fun `type comment structural mismatch flat tuple to scalar`() = test("""
      expr = undefined()  # type: str, int
      #│     │                    ^^^^^^^^ WARNING Type comment cannot be matched with unpacked variables
      #│     ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #└ TYPE Unknown
      """)

    @Test
    @TestFor(issues = ["PY-21191"])
    fun `type comment structural mismatch nested tuple arity`() = test("""
      _, (_, expr) = undefined()  # type: str, (str, str, int)
      #         │    │                    ^^^^^^^^^^^^^^^^^^^^ WARNING Type comment cannot be matched with unpacked variables
      #         │    ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #         └ TYPE Unknown
      """)

    @Test
    @TestFor(issues = ["PY-21191"])
    fun `type comment structural mismatch nested vs flat`() = test("""
      _, (_, expr) = undefined()  # type: (str, str), int
      #         │    │                    ^^^^^^^^^^^^^^^ WARNING Type comment cannot be matched with unpacked variables
      #         │    ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #         └ TYPE Unknown
      """)

    @Test
    @TestFor(issues = ["PY-21191"])
    fun `type comment with nested tuples in with statement`() = test("""
      with undefined() as (_, (_, expr)):  # type: str, (str, int)
      #    │                     └ TYPE tuple[str, int] FIXME int
      #    ^^^^^^^^^ ERROR Unresolved reference 'undefined'
          pass
      """)

    @Test
    @TestFor(issues = ["PY-21191"])
    fun `type comment with nested tuples in for statement`() = test("""
      for (_, (_, expr)) in undefined():  # type: str, (str, int)
      #            │        ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #            └ TYPE int
          pass
      """)
  }

  @Nested
  inner class FunctionTypeComments {
    @Test
    fun `function type comment`() = test("""
      from typing import List
      
      def f(x, *args, **kwargs):
          # type: (int, *float, **str) -> List[bool]
          pass
      
      expr = f
      # └ TYPE (x: int, *args: float | int, **kwargs: str) -> list[bool]
      """)

    @Test
    @TestFor(issues = ["PY-18595"])
    fun `function type comment for static method`() = test(
      // The deliberately wrong `-> bool` return annotation triggers a return-type warning that the
      // daemon emits a non-deterministic number of times; the original test only inferred the
      // parameter type, so the type checker is disabled here to keep the focus on that.
      TestOptions(
        disableInspections = setOf(
          PyUnresolvedReferencesInspection::class.java,
          PyCallingNonCallableInspection::class.java,
          PyTypeCheckerInspection::class.java,
        ),
      ),
      """
      class C:
          @staticmethod
          def m(some_int, some_bool, some_str):
              # type: (int, bool, str) -> bool
              expr = some_int
      #       └ TYPE int
      """,
    )

    @Test
    @TestFor(issues = ["PY-18598"])
    fun `function type comment ellipsis parameters`() = test("""
      def f(x, y=42, z='foo'):
          # type: (...) -> int 
          pass
      
      expr = f
      #└ TYPE (x: Unknown, y: Unknown, z: Unknown) -> int
      """)

    @Test
    @TestFor(issues = ["PY-20421"])
    fun `function type comment single element tuple`() = test("""
      from typing import Tuple
      
      def f():
          # type: () -> Tuple[int]
          pass
      
      expr = f()
      #└ TYPE tuple[int]
      """)

    @Test
    @TestFor(issues = ["PY-18741"])
    fun `function type comment with param type comment`() = test("""
      def f(x, # type: int 
            y # type: bool
            ,z):
          # type: (...) -> str
          pass
      
      expr = f
      #└ TYPE (x: int, y: bool, z: Unknown) -> str
      """)

    @Test
    @TestFor(issues = ["PY-18877"])
    fun `function type comment on the same line`() = test("""
      def f(x,
            y):  # type: (int, int) -> None
          pass
      
      expr = f
      #└ TYPE (x: int, y: int) -> None
      """)

    @Test
    fun `function type comment in stubs`() = test(
      """
      from module import func
      
      expr = func()
      #└ TYPE MyClass
      """,
      "module.py" to """
        class MyClass(object):
            pass
        
        
        def func():
            # type: () -> MyClass
            pass
        """,
    )
  }

  @Nested
  inner class TypeCommentInspection {
    @Test
    fun `type comment assignment mismatch`() = test("""
      def f():
          x = 0  # type: str
      #       └ WARNING Expected type 'str', got 'Literal[0]' instead
          y = 1  # type: int
      """)
  }
}
