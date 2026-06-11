package com.intellij.python.lsp.core.type

import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyCapturePattern
import com.jetbrains.python.psi.PyClassPattern
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyLambdaExpression
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyLiteralPattern
import com.jetbrains.python.psi.PyMappingPattern
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyPrefixExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySequencePattern
import com.jetbrains.python.psi.PySetCompExpression
import com.jetbrains.python.psi.PySetLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyYieldExpression

class LspCollectSupportedTypesVisitorTest : PyTestCase() {
  fun `test visitor collects all supported types`() {
    myFixture.configureByText("test.py", """
      import typing
      from typing import Dict, List, Set
      
      # PyFunction
      @property  # PyDecorator
      def test_function(param: int) -> str:  # PyNamedParameter
          # PyReferenceExpression, PyTargetExpression, PyBinaryExpression
          x = y + z
          
          # PyCallExpression
          result = some_function(1, 2, 3)
          
          # PyListLiteralExpression
          list_val = [1, 2, 3]
          
          # PyDictLiteralExpression
          dict_val = {"key": "value", "num": 42}
          
          # PySetLiteralExpression
          set_val = {1, 2, 3}
          
          # PySetCompExpression
          set_comp = {x for x in range(10) if x % 2 == 0}
          
          # PySubscriptionExpression
          indexed = list_val[0]
          
          # PyPrefixExpression
          negative = -x
          not_val = not True
          
          # PyYieldExpression
          yield result
      
      # PyLambdaExpression
      lambda_func = lambda a, b: a + b
      
      # Pattern matching examples (Python 3.10+)
      def match_example(value):
          match value:
              # PyLiteralPattern
              case 42:
                  return "literal"
              # PyCapturePattern
              case x:
                  return f"captured {x}"
              # PyWildcardPattern
              case _:
                  return "wildcard"
              # PySequencePattern
              case [first, *rest]:
                  return f"sequence: first={first}, rest={rest}"
              # PyMappingPattern
              case {"key": val, **other}:
                  return f"mapping: key={val}, other={other}"
              # PyClassPattern
              case dict() as d:
                  return f"class pattern: {d}"
    """.trimIndent())

    val visitor = LspCollectSupportedTypesVisitor()
    myFixture.file.accept(visitor)

    val results = visitor.result

    // Verify specific type instances
    assertTrue("Should have PyMappingPattern instances", results.any { it is PyMappingPattern })
    assertTrue("Should have PySequencePattern instances", results.any { it is PySequencePattern })
    assertTrue("Should have PyBinaryExpression instances", results.any { it is PyBinaryExpression })
    assertTrue("Should have PyCallExpression instances", results.any { it is PyCallExpression })
    assertTrue("Should have PyCapturePattern instances", results.any { it is PyCapturePattern })
    assertTrue("Should have PyClassPattern instances", results.any { it is PyClassPattern })
    assertTrue("Should have PyDecorator instances", results.any { it is PyDecorator })
    assertTrue("Should have PyDictLiteralExpression instances", results.any { it is PyDictLiteralExpression })
    assertTrue("Should have PyFunction instances", results.any { it is PyFunction })
    assertTrue("Should have PyLambdaExpression instances", results.any { it is PyLambdaExpression })
    assertTrue("Should have PyListLiteralExpression instances", results.any { it is PyListLiteralExpression })
    assertTrue("Should have PyLiteralPattern instances", results.any { it is PyLiteralPattern })
    assertTrue("Should have PyNamedParameter instances", results.any { it is PyNamedParameter })
    assertTrue("Should have PyPrefixExpression instances", results.any { it is PyPrefixExpression })
    assertTrue("Should have PyReferenceExpression instances", results.any { it is PyReferenceExpression })
    assertTrue("Should have PySetCompExpression instances", results.any { it is PySetCompExpression })
    assertTrue("Should have PySetLiteralExpression instances", results.any { it is PySetLiteralExpression })
    assertTrue("Should have PySubscriptionExpression instances", results.any { it is PySubscriptionExpression })
    assertTrue("Should have PyTargetExpression instances", results.any { it is PyTargetExpression })
    assertTrue("Should have PyYieldExpression instances", results.any { it is PyYieldExpression })
  }
}