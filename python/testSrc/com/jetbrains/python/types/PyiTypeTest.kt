// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

/**
 * Type inference tests for `.pyi` stub files: a `.py` implementation without annotations paired with
 * a same-named (or sibling-module) `.pyi` stub that supplies the types.
 */
class PyiTypeTest : PyCodeInsightTestCase() {

  @Test
  fun `function parameter`() = test(
    "FunctionParameter.py",
    """
    def f(x):
    #     └ TYPE int
        pass
    """,
    "FunctionParameter.pyi" to "def f(x: int) -> None: ...",
  )

  @Test
  fun `function return type`() = test(
    TestOptions(enablePyAnyType = false),
    "FunctionReturnType.py",
    """
    def f():
        pass
    
    x = f()
    #\ TYPE int | None
    """,
    "FunctionReturnType.pyi" to """
      from typing import Optional
      
      
      def f() -> Optional[int]: ...
      """,
  )

  @Test
  fun `function type`() = test(
    "FunctionType.py",
    """
    def f(x):
    #   └ TYPE (x: int) -> dict[Unknown, Unknown]
        pass
    """,
    "FunctionType.pyi" to "def f(x: int) -> dict: ...",
  )

  @Test
  fun `module attribute`() = test(
    "ModuleAttribute.py",
    """
    x = None # WARNING Expected type 'int', got 'None' instead
    #\ TYPE int
    """,
    "ModuleAttribute.pyi" to "x = ...  # type: int",
  )

  @Test
  fun `coroutine type`() = test(
    TestOptions(enablePyAnyType = false),
    "CoroutineType.py",
    """
    async def f():
        return 42
    
    coroutine = f()
    #\ TYPE CoroutineType[Any, Any, int]
    """,
    "CoroutineType.pyi" to """
      async def f() -> int:
          ...
      """,
  )

  @Test
  fun `overloaded return type`() = test(
    "OverloadedReturnType.py",
    """
    def f(x):
        pass
    
    
    x = f('foo')
    #\ TYPE str
    """,
    "OverloadedReturnType.pyi" to """
      from typing import overload
      
      
      @overload
      def f(x: int) -> int: ...
      @overload
      def f(x: str) -> str: ...
      """,
  )

  @Test
  @TestFor(issues = ["PY-22808"])
  fun `overloaded not matched type`() = test(
    """
    from typing import Any
    from m1 import C
    
    def f(x: Any):
        c = C()
        expr = c.foo(x)
    #   └ TYPE list[Unknown] | Unknown
    """,
    "m1.pyi" to """
      from typing import overload
      
      class C[T]:
          @overload
          def foo(self, i: int) -> T: ...
          @overload
          def foo(self, s: slice) -> list[T]: ...
      """,
  )

  @Test
  @TestFor(issues = ["PY-22808"])
  fun `overloaded not matched generic type`() = test(
    """
    from m1 import C
    
    def f(x: list):
        c = C()
        expr = c.foo(non_existing=0)
    #   │           │              └ WARNING No overload of 'foo' matches the arguments. Argument types: (non_existing=Literal[0]). Expected one of: (i: int), (s: str)
    #   │           ^^^^^^^^^^^^^^^^ WARNING No overload of 'foo' matches the arguments. Argument types: (non_existing=Literal[0]). Expected one of: (i: int), (s: str)
    #   └ TYPE Unknown
    """,
    "m1.pyi" to """
      from typing import TypeVar, Generic, overload, List, Dict
      
      _T = TypeVar('_T')
      
      class C(Generic[_T]):
          @overload
          def foo(self, i: int) -> Dict[str, _T]: ...
          @overload
          def foo(self, s: str) -> List[_T]: ...
      """,
  )

  @Test
  fun `generic class definition in other file`() = test(
    """
    from other import Holder
    
    expr = Holder(42).get()
    #\ TYPE int
    """,
    "other.pyi" to """
      from typing import Generic, TypeVar
      
      T = TypeVar('T')
      
      
      class Holder(Generic[T]):
          def __init__(self, x: T):
              pass
      
          def get(self) -> T:
              pass
      """,
    "other.py" to """
      class Holder:
          def __init__(self, x):
              self.x = x
      
          def get(self):
              return self.x
      """,
  )

  @Test
  @TestFor(issues = ["PY-27186"])
  fun `generic class definition in same file`() = test(
    "main.py",
    """
    class Holder:
        def __init__(self, x):
            self.x = x # WARNING Unresolved attribute reference 'x' for class 'Holder'

        def get(self):
            return self.x # WARNING Unresolved attribute reference 'x' for class 'Holder'


    expr = Holder(42).get()
    # └ TYPE int
    """,
    "main.pyi" to """
      from typing import Generic, TypeVar
      
      T = TypeVar('T')
      
      
      class Holder(Generic[T]):
          def __init__(self, x: T):
              pass
      
          def get(self) -> T:
              pass
      """,
  )

  @Test
  fun `comparison operator overloads`() = test(
    """
    from lib import MyClass
    
    expr = 42 < MyClass(42) < MyClass('foo')
    #\ TYPE int
    """,
    "lib.pyi" to """
      from typing import overload, Generic, TypeVar
      
      T = TypeVar('T')
      
      
      class MyClass(Generic[T]):
          def __init__(self, x: T):
              pass
      
          @overload
          def __lt__(self, other: MyClass) -> T:
              pass
      
          @overload
          def __lt__(self, other: str) -> bool:
              pass
      
          def __gt__(self, other: int) -> bool:
              pass
      """,
    "lib.py" to """
      class MyClass:
          def __init__(self, *args):
              pass
      
          def __lt__(self, other):
              pass
      
          def __gt__(self, other):
              return True
      """,
  )

  @Test
  @TestFor(issues = ["PY-24929"])
  fun `instance attribute annotation`() = test(
    "InstanceAttributeAnnotation.py",
    """
    class C:
        def __init__(self):
            self.attr = None # WARNING Expected type 'int', got 'None' instead
    
    C().attr
    #   └ TYPE int
    """,
    "InstanceAttributeAnnotation.pyi" to """
      class C:
          attr: int
      """,
  )
}
