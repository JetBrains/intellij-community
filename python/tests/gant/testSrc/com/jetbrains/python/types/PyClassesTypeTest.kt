// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyClassesTypeTest : PyCodeInsightTestCase() {
  @Test
  @TestFor(issues = ["PY-79173"])
  fun `init subclass argument type mismatch`() = test("""
    class A:
        def __init_subclass__(cls, a: int): ...


    class B(A, a=123): ...

    class C(A, a="str"): ...
    #          ^^^^^^^ WARNING Expected type 'int', got 'Literal["str"]' instead
    """.trimIndent())

  @Test
  fun `old style classes`() = test("""
    def test_old_style_classes():
        class C:
            pass
        def f(x):
            '''
            :type x: object
            '''
            pass
        f(C()) #pass
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-6606"])
  fun `builtin base class`() = test("""
    def test():
        class MyInt(int):
            pass
        def f(x):
            '''
            :type x: MyInt
            '''
        i = MyInt(2)
        f(i)
    """.trimIndent())

  /**
   * A read-write attribute is invariant, so re-annotating an inherited attribute with a different type is an error.
   */
  @Nested
  @TestFor(issues = ["PY-90863"])
  inner class MutableAttributeOverrideVariance {

    @Test
    fun `overriding with a subtype violates invariance`() = test("""
      class A:
          a: int

      class B(A):
          a: bool  # WARNING Type 'bool' of the overridden attribute must be the inherited type 'int', because a mutable attribute is invariant
      """.trimIndent())

    @Test
    fun `unannotated reassignment keeps the inherited annotated literal type`() = test("""
      from typing import Literal

      class A:
          x: Literal[5]

      class C(A):
          def __init__(self) -> None:
              self.x = 5

      expr = C().x
      # └ TYPE Literal[5]
      """.trimIndent())

    @Test
    fun `unannotated reassignment keeps the inherited annotated literal union type`() = test("""
      from typing import Literal

      class A:
          x: Literal[5, 6]

      class C(A):
          def __init__(self) -> None:
              self.x = 5

      expr = C().x
      # └ TYPE Literal[5, 6]
      """.trimIndent())

    @Test
    fun `local variable in a method does not take the type of an inherited attribute`() = test("""
      class A:
          name: str

      class B(A):
          def f(self) -> None:
              name = 42
              expr = name
      #         └ TYPE Literal[42]
      """.trimIndent())

    @Test
    fun `overriding an attribute of a generic base with its type argument is allowed`() = test("""
      from typing import Generic, TypeVar

      T = TypeVar("T")

      class A(Generic[T]):
          a: T

      class B(A[int]):
          a: int

      class C(A[int]):
          a: bool  # WARNING Type 'bool' of the overridden attribute must be the inherited type 'int', because a mutable attribute is invariant
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-89201"])
    fun `unannotated override with an incompatible value is an error`() = test("""
      class A:
          a: int

      class B(A):
          a = "str"  # WARNING Expected type 'int', got 'str' instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-89201"])
    fun `unannotated override with a subtype value is allowed`() = test("""
      class A:
          a: int

      class B(A):
          a = True
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-89201"])
    fun `unannotated override of an inferred base attribute is not checked by default`() = test("""
      class A:
          a = 1

      class B(A):
          a = "str"
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-89201"])
    fun `unannotated override of an inferred base attribute is checked with the option`() = testWithInspection(
      PyTypeCheckerInspection().also { it.checkInferredAttributeOverrides = true },
      """
      class A:
          a = 1

      class B(A):
          a = <warning descr="Expected type 'int', got 'str' instead">"str"</warning>
      """.trimIndent())

    @Test
    fun `override of an imported attribute reads the stubs`() {
      test("""
        from mod import A

        class B(A):
            a: bool  # WARNING Type 'bool' of the overridden attribute must be the inherited type 'int', because a mutable attribute is invariant
        """.trimIndent(),
        "mod.py" to """
          class A:
              a: int
          """)
      assertFileNotParsed("mod.py")
    }

    @Test
    fun `overriding with a supertype is incompatible`() = test("""
      class A:
          a: int

      class B(A):
          a: object  # WARNING Type 'object' of the overridden attribute is not compatible with the inherited type 'int'
      """.trimIndent())

    @Test
    fun `overriding with an unrelated type is incompatible`() = test("""
      class A:
          a: int

      class B(A):
          a: str  # WARNING Type 'str' of the overridden attribute is not compatible with the inherited type 'int'
      """.trimIndent())

    @Test
    fun `overriding with the same type is allowed`() = test("""
      class A:
          a: int

      class B(A):
          a: int
      """.trimIndent())

    @Test
    fun `annotated override of an inferred base attribute is not checked by default`() = test("""
      class A:
          a = 1

      class B(A):
          a: str
      """.trimIndent())

    @Test
    fun `annotated override of an inferred base attribute is checked with the option`() = testWithInspection(
      PyTypeCheckerInspection().also { it.checkInferredAttributeOverrides = true },
      """
      class A:
          a = 1

      class B(A):
          a: <warning descr="Type 'str' of the overridden attribute is not compatible with the inherited type 'int'">str</warning>
      """.trimIndent())

    @Test
    fun `distinct attribute name is not an override`() = test("""
      class A:
          a: int

      class B(A):
          b: str
      """.trimIndent())

    @Test
    fun `override through a deeper ancestor`() = test("""
      class A:
          a: int

      class B(A):
          pass

      class C(B):
          a: bool  # WARNING Type 'bool' of the overridden attribute must be the inherited type 'int', because a mutable attribute is invariant
      """.trimIndent())

    @Test
    fun `overriding a frozen dataclass field with a subtype is allowed`() = test("""
      from dataclasses import dataclass

      @dataclass(frozen=True)
      class A:
          a: int

      @dataclass(frozen=True)
      class B(A):
          a: bool
      """.trimIndent())

    @Test
    fun `overriding a frozen dataclass field with an unrelated type is incompatible`() = test("""
      from dataclasses import dataclass

      @dataclass(frozen=True)
      class A:
          a: int

      @dataclass(frozen=True)
      class B(A):
          a: str  # WARNING Type 'str' of the overridden attribute is not compatible with the inherited type 'int'
      """.trimIndent())

    @Test
    fun `overriding a field of a dataclass_transform decorator with frozen_default with a subtype is allowed`() = test("""
      from typing import dataclass_transform

      @dataclass_transform(frozen_default=True)
      def model(cls): ...

      @model
      class A:
          a: int

      @model
      class B(A):
          a: bool
      """.trimIndent())

    @Test
    fun `overriding a field of a dataclass_transform base class with frozen_default with a subtype is allowed`() = test("""
      from typing import dataclass_transform

      @dataclass_transform(frozen_default=True)
      class ModelBase: ...

      class A(ModelBase):
          a: int

      class B(A):
          a: bool
      """.trimIndent())

    @Test
    fun `overriding a field of a dataclass_transform decorator with frozen false with a subtype violates invariance`() = test("""
      from typing import Callable, TypeVar, dataclass_transform

      T = TypeVar("T")

      @dataclass_transform(frozen_default=True)
      def model(*, frozen: bool = True) -> Callable[[T], T]: ...

      @model(frozen=False)
      class A:
          a: int

      @model(frozen=False)
      class B(A):
          a: bool  # WARNING Type 'bool' of the overridden attribute must be the inherited type 'int', because a mutable attribute is invariant
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-87917"])
    fun `overriding a named tuple field with a subtype violates invariance`() = test("""
      from typing import NamedTuple

      class A(NamedTuple):
          a: int

      class B(A):
          a: bool  # WARNING Type 'bool' of the overridden NamedTuple field must be the inherited type 'int', because the subclass keeps the NamedTuple constructor
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-87917"])
    fun `override of an imported frozen dataclass field reads the stubs`() {
      test("""
        from dataclasses import dataclass
        from mod import A

        @dataclass(frozen=True)
        class B(A):
            a: bool
        """.trimIndent(),
        "mod.py" to """
          from dataclasses import dataclass

          @dataclass(frozen=True)
          class A:
              a: int
          """)
      assertFileNotParsed("mod.py")
    }

    @Test
    @TestFor(issues = ["PY-87917"])
    fun `override of an imported named tuple field reads the stubs`() {
      test("""
        from mod import A

        class B(A):
            a: bool  # WARNING Type 'bool' of the overridden NamedTuple field must be the inherited type 'int', because the subclass keeps the NamedTuple constructor
        """.trimIndent(),
        "mod.py" to """
          from typing import NamedTuple

          class A(NamedTuple):
              a: int
          """)
      assertFileNotParsed("mod.py")
    }

    @Test
    fun `overriding a class variable of a frozen dataclass with a subtype violates invariance`() = test("""
      from dataclasses import dataclass
      from typing import ClassVar

      @dataclass(frozen=True)
      class A:
          a: ClassVar[int] = 1

      @dataclass(frozen=True)
      class B(A):
          a: ClassVar[bool] = True  # WARNING Type 'bool' of the overridden attribute must be the inherited type 'int', because a mutable attribute is invariant
      """.trimIndent())

    @Test
    fun `overriding a mutable attribute in a frozen dataclass with a subtype violates invariance`() = test("""
      from dataclasses import dataclass

      class A:
          a: int

      @dataclass(frozen=True)
      class B(A):
          a: bool  # WARNING Type 'bool' of the overridden attribute must be the inherited type 'int', because a mutable attribute is invariant
      """.trimIndent())

    @Test
    fun `covariant attribute overrides allow a subtype but still reject an unrelated type`() = testWithInspection(
      PyTypeCheckerInspection().also { it.allowCovariantAttributeOverrides = true },
      """
      class A:
          a: int

      class B(A):
          a: bool

      class C(A):
          a: <warning descr="Type 'str' of the overridden attribute is not compatible with the inherited type 'int'">str</warning>
      """.trimIndent())

    private fun testWithInspection(inspection: LocalInspectionTool, text: String) {
      myFixture.configureByText("aaa.py", text)
      myFixture.enableInspections(inspection)
      myFixture.checkHighlighting(true, false, false)
    }
  }
}
