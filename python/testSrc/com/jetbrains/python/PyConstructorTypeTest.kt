// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyInspectionTestCase
import com.jetbrains.python.inspections.PyArgumentListInspection
import com.jetbrains.python.inspections.PyAssertTypeInspection
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection

internal class PyConstructorTypeTest : PyInspectionTestCase() {
  override fun getInspectionClass(): Class<PyAssertTypeInspection> = PyAssertTypeInspection::class.java

  override fun getAdditionalInspectionClasses(): List<Class<out LocalInspectionTool>> = listOf(
    PyArgumentListInspection::class.java,
    PyTypeCheckerInspection::class.java,
    PyUnresolvedReferencesInspection::class.java
  )

  override fun getTestCaseDirectory(): String = "inspections/PyConstructorType/"

  fun `test metaclass __call__ with incompatible return type`() {
    doTestByText("""
      from typing import assert_type, Self

      class Meta(type):
          def __call__[T](cls, t: T) -> list[T]: ...

      class MyGeneric[T](metaclass=Meta): ...

      assert_type(MyGeneric[int](1), list[int]) # PY-88644
      assert_type(MyGeneric[int](1.0), list[float]) # PY-88644
      assert_type(MyGeneric(1), list[int])
      assert_type(MyGeneric(1.0), list[float])
      

      class MyClass(metaclass=Meta):
          def __new__(cls) -> Self: ...
      
      assert_type(MyClass('ab'), list[str])
      """.trimIndent()
    )
  }

  fun `test metaclass __call__ returns type var`() {
    doTestByText("""
      from typing import assert_type, Self
      
      class Meta(type):
          def __call__[T](cls: type[T], *args, **kwargs) -> T: ...
      
      class MyClass(metaclass=Meta):
          def __new__(cls, p) -> Self: ...
      
      assert_type(MyClass(1), MyClass)
      MyClass(<warning descr="Parameter 'p' unfilled">)</warning>
      """.trimIndent())
  }

  fun `test metaclass __call__ returns derived class instance`() {
    doTestByText("""
      from typing import assert_type, Self
      
      class Meta(type):
          def __call__(self) -> 'Derived': ...
      
      class Base(metaclass=Meta):
          def __new__(cls, *args, **kwargs) -> Self: ...
      
      class Derived(Base): ...
      
      assert_type(Base(), Base)
      assert_type(Derived(), Derived)
      """.trimIndent())
  }

  fun `test metaclass __call__ returns object`() {
    doTestByText("""
      from typing import assert_type, Self
      
      class Meta(type):
          def call(cls) -> object: ...
      
          __call__ = call
      
      class MyClass(metaclass=Meta):
          def __new__(cls, p) -> Self: ...
      
      assert_type(MyClass(), object)
      MyClass(<warning descr="Unexpected argument">1</warning>)
      """.trimIndent())
  }

  fun testMetaclassCallReturnsObjectMultifile() {
    doMultiFileTest("a.py")
  }

  fun `test metaclass __call__ not annotated`() {
    doTestByText("""
      from typing import assert_type, Self
      
      class Meta(type):
          def __call__(cls, p: int): ...
      
      class MyClass(metaclass=Meta):
          def __new__(cls, *args, **kwargs) -> Self: ...
      
      assert_type(MyClass(1), MyClass)
      MyClass(1, 2) # TODO (PY-80602): Missing error 'Unexpected argument'
      """.trimIndent())
  }

  @TestFor(issues = ["PY-77611"])
  fun `test __new__ with incompatible return type`() {
    doTestByText("""
      from typing import assert_type

      class C:
          def __new__(cls) -> int:
              return 1

          def __init__(self, x: int):
              ...

      assert_type(C(), int)
      C(<warning descr="Unexpected argument">1</warning>)
      
      
      class Base:
          def __new__(cls, x: int) -> Base: ...

      class Derived(Base):
          def __init__(self): ...

      assert_type(Derived(1), Base)
      Derived(<warning descr="Parameter 'x' unfilled">)</warning>
    """.trimIndent())
  }

  fun `test generic class __new__ with compatible return type`() {
    doTestByText("""
      from typing import assert_type, Self
      
      class MyClass[T]:
          def __new__(cls, _: T) -> Self:
              return super().__new__(cls)

      assert_type(MyClass[int](1), MyClass[int])
      assert_type(MyClass[float](1), MyClass[float])
      MyClass[int](<warning descr="Expected type 'int' (matched generic type 'T'), got 'float' instead">1.0</warning>)

      assert_type(MyClass(1), MyClass[int])
      assert_type(MyClass(1.0), MyClass[float])
      """.trimIndent()
    )
  }

  fun `test generic class __new__ with incompatible return type`() {
    doTestByText("""
      from typing import assert_type
      
      class MyClass[T]:
          def __new__(cls, t: T) -> list[T]:
              return [t]
      
      assert_type(MyClass[int](1), list[int]) # PY-88644
      assert_type(MyClass[float](1), list[float]) # PY-88644
      MyClass[int](<warning descr="Expected type 'int' (matched generic type 'T'), got 'float' instead">1.0</warning>)

      assert_type(MyClass(1), list[int])
      assert_type(MyClass(1.0), list[float])
      """.trimIndent()
    )
  }

  fun `test __new__ attribute assignment`() {
    doTestByText("""
      from typing import assert_type

      def func(x: type[Class], y: int) -> str:
          raise NotImplementedError

      class Class:
          __new__ = func

      Class(<warning descr="Parameter 'y' unfilled">)</warning>
      assert_type(Class(1), str)
    """.trimIndent())
  }

  fun `test __init__ solves type vars left unsolved by __new__`() {
    doTestByText("""
      from typing import assert_type, Self

      class MyClass[T]:
          def __new__(cls, *args, **kwargs) -> Self:
              return super().__new__(cls)

          def __init__(self, x: T) -> None:
              pass

      assert_type(MyClass(1), MyClass[int])
      """.trimIndent()
    )
  }

  @TestFor(issues = ["PY-88644"])
  fun `test __new__ overrides generic type parameter`() {
    doTestByText("""
      from typing import assert_type

      class MyClass[T]:
          def __new__(cls, *args, **kwargs) -> "MyClass[list[T]]":
              ...

      assert_type(MyClass[int](), MyClass[list[int]])
      """.trimIndent()
    )
  }

  fun `test generic class __init__`() {
    doTestByText("""
      from typing import assert_type
      
      class MyClass[T]:
          def __init__(self, _: T): ...

      assert_type(MyClass[int](1), MyClass[int])
      assert_type(MyClass[float](1), MyClass[float])
      MyClass[int](<warning descr="Expected type 'int' (matched generic type 'T'), got 'float' instead">1.0</warning>)
      
      assert_type(MyClass(1), MyClass[int])
      assert_type(MyClass(1.0), MyClass[float])
      """.trimIndent()
    )
  }

  fun `test annotated self in generic class __init__`() {
    doTestByText("""
      class MyClass[T]:
          def __init__(self: "MyClass[int]") -> None: ...
                   
      MyClass()
      MyClass[int]()
      <warning descr="Expected type 'MyClass[int]', got 'MyClass[str]' instead">MyClass[str]</warning>()
      """.trimIndent())
  }

  fun `test __init__ attribute assignment`() {
    doTestByText("""
      from typing import assert_type

      class Class[T]:
          def initialize(self: Class[int], x: str) -> None: ...

          __init__ = initialize

      Class(<warning descr="Parameter 'x' unfilled">)</warning>
      assert_type(Class("abb"), Class[int])
    """.trimIndent())
  }
}
