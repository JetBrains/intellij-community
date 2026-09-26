// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.fixtures.PyCodeInsightTestCase.TestInspections
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@Layers.Functional
@Subsystems.Inspections
@TestFor(classes = [PyDivisionByZeroInspection::class], issues = ["PY-40880"])
@TestInspections(enableInspections = [PyDivisionByZeroInspection::class])
class PyDivisionByZeroInspectionTest : PyCodeInsightTestCase() {

  @Nested
  inner class BinaryOperators {
    @Test
    fun `division by zero`() = test("""
      1 / 0  # WARNING Division by zero
      1 // 0 # WARNING Division by zero
      1 % 0  # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `augmented division by zero`() = test("""
      x = 1
      x /= 0  # WARNING Division by zero
      x //= 0 # WARNING Division by zero
      x %= 0  # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `division by zero in any numeric literal format`() = test("""
      1 / 0.0 # WARNING Division by zero
      1 / 0e0 # WARNING Division by zero
      1 / 0j  # WARNING Division by zero
      1 / 0x0 # WARNING Division by zero
      1 / 0o0 # WARNING Division by zero
      1 / 0b0 # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `division by unary minus zero`() = test("""
      1 / -0 # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `division by parenthesized zero`() = test("""
      1 / ((-(+((0))))) # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `division by non-zero literal is not reported`() = test("""
      1 / 2
      """.trimIndent())

    @Test
    fun `division by variable is not reported`() = test("""
      def f(x: int, y: int):
          return x / y
      """.trimIndent())

    @Test
    fun `division by zero on operand of unknown type is not reported`() = test("""
      def f(x):
          return x / 0
      """.trimIndent())

    @Test
    fun `division by zero-valued variable`() = test("""
      zero = 0
      1 / zero # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `division by conditionally zero-valued variable is not reported`() = test("""
      if input():
          zero = 0
      else:
          zero = 1
      1 / zero
      """.trimIndent())

    @Test
    fun `division by false`() = test("""
      1 / False # WARNING Division by zero

      zero = False
      1 / zero  # WARNING Division by zero
      1 / True
      """.trimIndent())

    @Test
    fun `division by zero-valued local variable`() = test("""
      def f():
          zero = 0
          return 1 / zero # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `division by variable from outer scope is not reported`() = test("""
      zero = 0

      def f():
          return 1 / zero
      """.trimIndent())

    @Test
    fun `division by module-level variable changed in another function is not reported`() = test("""
      count = 0

      def increment():
          global count
          count += 1

      def average(total: int):
          return total / count

      increment()
      1 / count
      """.trimIndent())

    @Test
    fun `division by local variable changed in nested function is not reported`() = test("""
      def f():
          count = 0

          def increment():
              nonlocal count
              count += 1

          increment()
          return 1 / count
      """.trimIndent())

    @Test
    fun `division by instance attribute changed in another method is not reported`() = test("""
      class Counter:
          def __init__(self):
              self.count = 0

          def increment(self):
              self.count = self.count + 1

          def average(self, total: int):
              return total / self.count
      """.trimIndent())

    @Test
    fun `division by zero on union with a member not raising is not reported`() = test("""
      class C:
          def __truediv__(self, other):
              return other

      def f(x: int | str, y: int | C, z: int | float):
          x % 0
          y / 0
          z / 0 # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `division by zero on class object is not reported`() = test("""
      int / 0 # WARNING Expected type 'int', got 'type[int]' instead
      """.trimIndent())

    @Test
    fun `highlighting only the division binary expression`() = test("""
      x = 0 - (1 + 2) * 3 / 0 * (4 + 5) - 6
      #       ^^^^^^^^^^^^^^^ WARNING Division by zero
      """.trimIndent())

    @Test
    fun `string formatting with percent operator is not reported`() = test("""
      "%d" % 0
      b"%d" % 0
      bytearray(b"%d") % 0
      """.trimIndent())

    @Test
    fun `augmented string formatting with percent operator is not reported`() = test("""
      s = "%d"
      s %= 0
      
      b = b"%d"
      b %= 0
      
      ba = bytearray(b"%d")
      ba %= 0
      """.trimIndent())

    @Test
    fun `division by zero on custom class not supporting division is not reported`() = test("""
      class C: ...
      
      C() / 0  # WARNING Expected type 'int', got 'C' instead
      C() // 0 # WARNING Expected type 'int', got 'C' instead
      C() % 0  # WARNING Expected type 'int', got 'C' instead
      """.trimIndent())

    @Test
    fun `division by zero on custom class defining division operators is not reported`() = test("""
      class C:
          def __truediv__(self, other):
              return other
          def __floordiv__(self, other):
              return other
          def __mod__(self, other):
              return other
          def __itruediv__(self, other):
              return other
          def __ifloordiv__(self, other):
              return other
          def __imod__(self, other):
              return other
      
      C() / 0
      C() // 0
      C() % 0
      
      x = C()
      x /= 0
      x //= 0
      x %= 0
      """.trimIndent())

    @Test
    fun `division by zero on various types`() = test("""
      from decimal import Decimal
      from fractions import Fraction
      
      1.0 / 0             # WARNING Division by zero
      1j / 0              # WARNING Division by zero
      Fraction(1, 2) / 0  # WARNING Division by zero
      Decimal(1) / 0      # WARNING Division by zero
      "" / 0              # WARNING Expected type 'int', got 'Literal[""]' instead
      
      1.0 // 0            # WARNING Division by zero
      1j // 0             # WARNING Expected type 'int', got 'complex' instead
      Fraction(1, 2) // 0 # WARNING Division by zero
      Decimal(1) // 0     # WARNING Division by zero
      "" // 0             # WARNING Expected type 'int', got 'Literal[""]' instead
      
      1.0 % 0             # WARNING Division by zero
      1j % 0              # WARNING Expected type 'int', got 'complex' instead
      Fraction(1, 2) % 0  # WARNING Division by zero
      Decimal(1) % 0      # WARNING Division by zero
      "" % 0
      """.trimIndent())

    @Test
    fun `division by zero on builtin subclass`() = test("""
      class MyInt(int): ...
      
      True / 0    # WARNING Division by zero
      MyInt() / 0 # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `augmented division by zero on builtin subclass overriding in-place operators is not reported`() = test("""
      class MyInt(int):
          def __itruediv__(self, other):
              return other
          def __ifloordiv__(self, other):
              return other
          def __imod__(self, other):
              return other
      
      x = MyInt()
      
      x /= 0
      x //= 0
      x %= 0
      
      x / 0  # WARNING Division by zero
      x // 0 # WARNING Division by zero
      x % 0  # WARNING Division by zero
      """.trimIndent())
  }

  @Nested
  inner class Divmod {
    @Test
    fun `divmod by zero`() = test("""
      divmod(1, 0) # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `divmod by non-zero is not reported`() = test("""
      divmod(1, 2)
      """.trimIndent())

    @Test
    fun `divmod on operand of unknown type is not reported`() = test("""
      def f(x):
          divmod(x, 0)
      """.trimIndent())

    @Test
    fun `divmod shadowed by local function is not reported`() = test("""
      def divmod(a, b): ...
      
      divmod(1, 0)
      """.trimIndent())

    @Test
    fun `divmod on custom class defining divmod is not reported`() = test("""
      class C:
          def __divmod__(self, other):
              return other
      
      divmod(C(), 0)
      """.trimIndent())

    @Test
    fun `divmod by zero on various types`() = test("""
      from decimal import Decimal
      from fractions import Fraction
      
      divmod(1.0, 0)            # WARNING Division by zero
      divmod(1j, 0)             # WARNING FIXME Expected type 'int', got 'complex' instead # PY-92229
      divmod(Fraction(1, 2), 0) # WARNING Division by zero
      divmod(Decimal(1), 0)     # WARNING Division by zero
      divmod("", 0)             # WARNING FIXME Expected type 'int', got 'Literal[""]' instead # PY-92229
      """.trimIndent())
  }

  @Nested
  inner class OperatorModule {
    @Test
    fun `operator module division by zero`() = test("""
      import operator
      
      operator.truediv(1, 0)  # WARNING Division by zero
      operator.itruediv(1, 0) # WARNING Division by zero
      
      operator.floordiv(1, 0)  # WARNING Division by zero
      operator.ifloordiv(1, 0) # WARNING Division by zero
      
      operator.mod(1, 0)  # WARNING Division by zero
      operator.imod(1, 0) # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `operator module division by non-zero is not reported`() = test("""
      import operator
      
      operator.truediv(1, 2)
      """.trimIndent())

    @Test
    // PY-92230: the parameter type inlay hint infers the type of `x` from its usages, which loads the AST of `_operator.truediv` in _operator.pyi
    @TestCaseOptions(assertSdkRootsNotParsed = false)
    fun `operator module division on operand of unknown type is not reported`() = test("""
      import operator
      
      def f(x):
          operator.truediv(x, 0)
      """.trimIndent())

    @Test
    fun `operator module division function shadowed by local function is not reported`() = test("""
      from operator import truediv
      
      def truediv(a, b): ...
      
      truediv(1, 0)
      """.trimIndent())

    @Test
    fun `aliased operator module division by zero`() = test("""
      from operator import truediv as div
      
      div(1, 0) # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `operator module division on custom class defining division operators is not reported`() = test("""
      import operator
      
      class C:
          def __truediv__(self, other):
              return other
      
      operator.truediv(C(), 0)
      """.trimIndent())

    @Test
    fun `operator module in-place division on builtin subclass overriding in-place operators is not reported`() = test("""
      import operator
      
      class MyInt(int):
          def __itruediv__(self, other):
              return other
          def __ifloordiv__(self, other):
              return other
          def __imod__(self, other):
              return other
      
      x = MyInt()
      
      operator.itruediv(x, 0)
      operator.ifloordiv(x, 0)
      operator.imod(x, 0)
      
      operator.truediv(x, 0)  # WARNING Division by zero
      operator.floordiv(x, 0) # WARNING Division by zero
      operator.mod(x, 0)      # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `operator module division by zero on various types`() = test("""
      import operator
      from decimal import Decimal
      from fractions import Fraction
      
      operator.truediv(1.0, 0)             # WARNING Division by zero
      operator.truediv(1j, 0)              # WARNING Division by zero
      operator.truediv(Fraction(1, 2), 0)  # WARNING Division by zero
      operator.truediv(Decimal(1), 0)      # WARNING Division by zero
      operator.truediv("", 0)              # WARNING FIXME Expected type 'int', got 'Literal[""]' instead # PY-92229
      
      operator.floordiv(1.0, 0)            # WARNING Division by zero
      operator.floordiv(1j, 0)             # WARNING FIXME Expected type 'int', got 'complex' instead # PY-92229
      operator.floordiv(Fraction(1, 2), 0) # WARNING Division by zero
      operator.floordiv(Decimal(1), 0)     # WARNING Division by zero
      operator.floordiv("", 0)             # WARNING FIXME Expected type 'int', got 'Literal[""]' instead # PY-92229
      
      operator.mod(1.0, 0)                 # WARNING Division by zero
      operator.mod(1j, 0)                  # WARNING FIXME Expected type 'int', got 'complex' instead # PY-92229
      operator.mod(Fraction(1, 2), 0)      # WARNING Division by zero
      operator.mod(Decimal(1), 0)          # WARNING Division by zero
      operator.mod("", 0)                  # WARNING FIXME Expected type 'int', got 'Literal[""]' instead # PY-92229
      """.trimIndent())
  }

  @Nested
  inner class Fraction {
    @Test
    fun `fraction by zero`() = test("""
      from fractions import Fraction
      
      Fraction(1, 0) # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `fraction by non-zero is not reported`() = test("""
      from fractions import Fraction
      
      Fraction(1, 2)
      """.trimIndent())

    @Test
    fun `fraction shadowed by local class is not reported`() = test("""
      from fractions import Fraction
      
      class Fraction:
          def __init__(self, a, b): ...
      
      Fraction(1, 0)
      """.trimIndent())

    @Test
    fun `aliased fraction by zero`() = test("""
      from fractions import Fraction as Frac
      
      Frac(1, 0) # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `fraction by zero keyword denominator`() = test("""
      from fractions import Fraction

      Fraction(1, denominator=0)           # WARNING Division by zero
      Fraction(numerator=1, denominator=0) # WARNING Division by zero
      Fraction(denominator=0, numerator=1) # WARNING Division by zero
      """.trimIndent())

    @Test
    fun `fraction with zero keyword numerator is not reported`() = test("""
      from fractions import Fraction

      Fraction(denominator=1, numerator=0)
      Fraction(numerator=0, denominator=1)
      """.trimIndent())
  }
}
