// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.quickFixes

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.PyAugmentAssignmentInspection
import com.jetbrains.python.inspections.PyCompatibilityInspection
import com.jetbrains.python.inspections.PyInconsistentIndentationInspection
import com.jetbrains.python.inspections.PyMissingOrEmptyDocstringInspection
import com.jetbrains.python.inspections.PySimplifyBooleanCheckInspection
import org.junit.jupiter.api.Test

/**
 * Applies quick fixes that every other test only asserts the presence of: the inspections these belong to are well
 * covered, their fixes are not.
 */
@TestFor(issues = ["PY-91546"])
@Subsystems.QuickFixes
@Components.Inspection
@Layers.Functional
class PyQuickFixApplicationTest : PyCodeInsightTestCase() {

  @Test
  @TestInspections(enableInspections = [PyAugmentAssignmentInspection::class])
  fun `an assignment adding to its own target becomes an augmented assignment`() {
    testQuickFix("""
      i = 0
      i = i + 1
    """, AUGMENT_ASSIGNMENT, """
      i = 0
      i += 1
    """)
  }

  // The operators the fix can move the target across are the commutative ones, so the target may sit on either side.
  @Test
  @TestInspections(enableInspections = [PyAugmentAssignmentInspection::class])
  fun `an assignment whose target is the right operand becomes an augmented assignment`() {
    testQuickFix("""
      i = 0
      i = 1 + i
    """, AUGMENT_ASSIGNMENT, """
      i = 0
      i += 1
    """)
  }

  @Test
  @TestInspections(enableInspections = [PyAugmentAssignmentInspection::class])
  fun `an assignment to a subscription becomes an augmented assignment`() {
    testQuickFix("""
      a = [0]
      a[0] = a[0] + 1
    """, AUGMENT_ASSIGNMENT, """
      a = [0]
      a[0] += 1
    """)
  }

  // Concatenation counts as well: the operand does not have to be a number, it only has to be a sequence.
  @Test
  @TestInspections(enableInspections = [PyAugmentAssignmentInspection::class])
  fun `an assignment concatenating to its own target becomes an augmented assignment`() {
    testQuickFix("""
      s = "a"
      s = s + "b"
    """, AUGMENT_ASSIGNMENT, """
      s = "a"
      s += "b"
    """)
  }

  // Subtraction is not commutative, so a target on the right of it cannot be moved into the operator.
  @Test
  @TestInspections(enableInspections = [PyAugmentAssignmentInspection::class])
  fun `an assignment subtracting its own target is left alone`() {
    assertNoQuickFix("""
      i = 0
      i = 1 - i
    """, AUGMENT_ASSIGNMENT)
  }

  @Test
  @TestInspections(enableInspections = [PySimplifyBooleanCheckInspection::class])
  fun `a comparison to True is replaced with the operand itself`() {
    testQuickFix("""
      def f(x: bool):
          if x == True:
              pass
    """, simplifyBooleanExpression("x"), """
      def f(x: bool):
          if x:
              pass
    """)
  }

  @Test
  @TestInspections(enableInspections = [PySimplifyBooleanCheckInspection::class])
  fun `an inequality to True is replaced with the negated operand`() {
    testQuickFix("""
      def f(x: bool):
          if x != True:
              pass
    """, simplifyBooleanExpression("not x"), """
      def f(x: bool):
          if not x:
              pass
    """)
  }

  @Test
  @TestInspections(enableInspections = [PySimplifyBooleanCheckInspection::class])
  fun `a comparison to False is replaced with the negated operand`() {
    testQuickFix("""
      def f(x: bool):
          if x == False:
              pass
    """, simplifyBooleanExpression("not x"), """
      def f(x: bool):
          if not x:
              pass
    """)
  }

  // The literal may be written first, and then the surviving operand is the right one.
  @Test
  @TestInspections(enableInspections = [PySimplifyBooleanCheckInspection::class])
  fun `a comparison whose left operand is the literal keeps the right one`() {
    testQuickFix("""
      def f(x: bool):
          if True == x:
              pass
    """, simplifyBooleanExpression("x"), """
      def f(x: bool):
          if x:
              pass
    """)
  }

  @Test
  @TestInspections(enableInspections = [PySimplifyBooleanCheckInspection::class])
  fun `an identity check against True is replaced with the operand itself`() {
    testQuickFix("""
      def f(x: bool):
          if x is True:
              pass
    """, simplifyBooleanExpression("x"), """
      def f(x: bool):
          if x:
              pass
    """)
  }

  @Test
  @TestInspections(enableInspections = [PySimplifyBooleanCheckInspection::class])
  fun `a comparison to an empty list is replaced with the negated operand`() {
    testQuickFix("""
      def f(x: list):
          if x == []:
              pass
    """, simplifyBooleanExpression("not x"), """
      def f(x: list):
          if not x:
              pass
    """)
  }

  @Test
  fun `a lowercase true is replaced with the keyword`() {
    testQuickFix("x = true", "Replace with True", "x = True")
  }

  @Test
  fun `a lowercase false is replaced with the keyword`() {
    testQuickFix("x = false", "Replace with False", "x = False")
  }

  @Test
  fun `a reference to an instance attribute is qualified with the first parameter`() {
    testQuickFix("""
      class C:
          def __init__(self):
              self.value = 1

          def get(self):
              return value
    """, addQualifier("value", "self"), """
      class C:
          def __init__(self):
              self.value = 1

          def get(self):
              return self.value
    """)
  }

  @Test
  fun `a reference to a method is qualified with the first parameter`() {
    testQuickFix("""
      class C:
          def helper(self):
              return 1

          def use(self):
              return helper()
    """, addQualifier("helper", "self"), """
      class C:
          def helper(self):
              return 1

          def use(self):
              return self.helper()
    """)
  }

  // The qualifier is whatever the enclosing method calls its first parameter, not the conventional `self`.
  @Test
  fun `a reference is qualified with an unconventionally named first parameter`() {
    testQuickFix("""
      class C:
          def __init__(this):
              this.value = 1

          def get(this):
              return value
    """, addQualifier("value", "this"), """
      class C:
          def __init__(this):
              this.value = 1

          def get(this):
              return this.value
    """)
  }

  @Test
  fun `a reference to a class level property is qualified with the first parameter`() {
    testQuickFix("""
      class C:
          value = property(lambda self: 1)

          def get(self):
              return value
    """, addQualifier("value", "self"), """
      class C:
          value = property(lambda self: 1)

          def get(self):
              return self.value
    """)
  }

  @Test
  @TestInspections(enableInspections = [PyMissingOrEmptyDocstringInspection::class])
  fun `a function without a docstring gets a stub for its parameter and return value`() {
    testQuickFix("""
      def f(x):
          return x
    """, INSERT_DOCSTRING, """
      def f(x):
          $tripleQuote

          :param x:
          :return:
          $tripleQuote
          return x
    """)
  }

  // Nothing to document beyond the summary line, so the stub is an empty docstring.
  @Test
  @TestInspections(enableInspections = [PyMissingOrEmptyDocstringInspection::class])
  fun `a function without parameters gets an empty docstring`() {
    testQuickFix("""
      def f():
          pass
    """, INSERT_DOCSTRING, """
      def f():
          $tripleQuote

          $tripleQuote
          pass
    """)
  }

  @Test
  @TestInspections(enableInspections = [PyInconsistentIndentationInspection::class])
  fun `a tab indent is converted to spaces`() {
    testQuickFix("def f():\n    x = 1\n\ty = 2\n", "Convert indents to spaces",
                 "def f():\n    x = 1\n    y = 2\n")
  }

  @Test
  @TestInspections(enableInspections = [PyInconsistentIndentationInspection::class])
  fun `a space indent is converted to tabs`() {
    testQuickFix("def f():\n    x = 1\n\ty = 2\n", "Convert indents to tabs",
                 "def f():\n\tx = 1\n\ty = 2\n")
  }

  // The compatibility inspection checks every Python version in unit test mode, and 2.7 knows no `f` prefix.
  @Test
  @TestInspections(enableInspections = [PyCompatibilityInspection::class])
  fun `an unsupported string prefix is removed`() {
    testQuickFix("x = f'foo'", "Remove leading F", "x = 'foo'")
  }

  // The fix exists for code that has to run on both Python 2 and Python 3, so it converts in either direction.
  @Test
  @TestInspections(enableInspections = [PyCompatibilityInspection::class])
  fun `an import of the python 3 builtins module is converted to its python 2 name`() {
    testQuickFix("import builtins", "Convert builtin module import to supported form", "import __builtin__")
  }

  // The Python 2 name is missing from the version of the file as well, so the `UnsupportedFeatures` annotator reports
  // the import next to the inspection and the same fix is offered by both.
  @Test
  @TestInspections(enableInspections = [PyCompatibilityInspection::class])
  fun `an import of the python 2 builtin module is converted to its python 3 name`() {
    testQuickFix("import __builtin__", "Convert builtin module import to supported form", "import builtins",
                 expectedMatchCount = 2)
  }

  private fun simplifyBooleanExpression(replacement: String): String = "Replace boolean expression with '$replacement'"

  private fun addQualifier(name: String, qualifier: String): String = "Replace '$name' with '$qualifier.$name'"

  companion object {
    private const val AUGMENT_ASSIGNMENT = "Replace assignment with augmented assignment"
    private const val INSERT_DOCSTRING = "Insert docstring"
  }
}
