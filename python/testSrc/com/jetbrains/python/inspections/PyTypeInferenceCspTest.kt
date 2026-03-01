// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyTypeInferenceCspTest : PyInspectionTestCase() {

  override fun getInspectionClass(): Class<out PyInspection?> {
    return PyTypeCheckerInspection::class.java
  }

  override fun getAdditionalInspectionClasses(): List<Class<out LocalInspectionTool?>?> {
    return mutableListOf(PyAssertTypeInspection::class.java)
  }

  override fun doTestByText(text: String) {
    super.doTestByText(text.trimIndent())
  }

  fun `test Constraint solver simple 1`() {
    doTestByText("""
      from typing import assert_type
  
      def bar[U](a: U, b: U) -> U:
        return None
  
      r1 = bar(1, "s")
      assert_type(r1, int|str)
      """)
  }

  fun `test Constraint solver simple 2`() {
    doTestByText("""
      from typing import assert_type
  
      class A(): ...
      class B(A): ...
      class C(A): ...
  
      def bar[U: A|None](a: U, b: U) -> U:
        return None
  
      r1 = bar(B(), C())
      assert_type(r1, B | C)
      """)
  }

  fun `test Constraint solver simple 3`() {
    doTestByText("""
      from typing import assert_type
  
      class A(): ...
      class B(A): ...
  
      def bar[U: (A,str)](a: U) -> U:
        return ""
  
      r1 = bar(B())
      assert_type(r1, A)
      """)
  }

  fun `test TV with constraints 1`() {
    doTestByText("""
      from typing import assert_type
  
      def compile[AnyStr: (str, int)](pattern: AnyStr) -> list[AnyStr]: ...
  
      res = compile("s")
      assert_type(res, list[str])
      """)
  }

  fun `test Unbound TV with default 1`() {
    doTestByText("""
      from typing import assert_type
  
      def f[T=int]() -> T: ...
  
      assert_type(f(), int)
      """)
  }

  fun `test Unbound TV with default 2`() {
    doTestByText("""
      from typing import assert_type
  
      def f[T=str](p: int | list[T]) -> T: ...
  
      assert_type(f(3), str)
      assert_type(f([True]), bool)
      """)
  }

  fun `test Empty tuple`() {
    doTestByText("""
      from typing import Sequence, assert_type, Never
  
      def test_seq[T](x: Sequence[T]) -> Sequence[T]:
        return x
  
      def func8(t3: tuple[()]):
        assert_type(test_seq(t3), Sequence[Never])
      """)
  }

  fun `test Attrs type per default`() {
    fixme("Support for combined CSPs necessary", AssertionError::class.java) {
      runWithAdditionalClassEntryInSdkRoots("packages")
      {
        doTestByText("""
        from typing import Any, assert_type
        import attr
    
        @attr.s
        class B1:
          x = attr.ib()
          y = attr.ib(default=0)
          z = attr.ib(default=attr.Factory(list))
  
        def f(b1: B1) :
          assert_type(b1.y, int)
          assert_type(b1.z, list[Any]) # currently it is list[_T]
        """)
      }
    }
  }

  fun `test Type per overload matching`() {
    doTestByText("""
      from typing import Any, assert_type, overload

      class Class5[T]:
        @overload
        def __init__(self: "Class5[list[int]]", value: int) -> None: ...
        @overload
        def __init__(self: "Class5[set[str]]", value: str) -> None: ...
        @overload
        def __init__(self, value: T) -> None:
            pass

        def __init__(self, value: Any) -> None:
          pass


      assert_type(Class5(0), Class5[list[int]])
      assert_type(Class5(""), Class5[set[str]])
      """)
  }

  fun `test Bound from return to argument`() {
    fixme("Support for combined CSPs necessary", AssertionError::class.java) {
      doTestByText("""
      from typing import Callable, assert_type

      def fooFun[U](f: Callable[[U], None]) -> U:
        return None
  
      r0 : str = fooFun(lambda p: assert_type(p, str)) # currently p is U
      """)
    }
  }

  fun `test Simple union via arguments`() {
    doTestByText("""
      from typing import assert_type

      def bar[U](a: U, b: U) -> U:
        return None
  
      r1 = bar(1, "s")
      assert_type(r1, int | str)
      """)
  }

  fun `test Match union bound 0`() {
    doTestByText("""
      from typing import assert_type, Callable
  
      def f2[T](arg: Callable[[T], None]) -> T:
        pass
  
      def callback(p: int) : ...
  
      r = f2(callback)
      assert_type(r, int)
      """)
  }

  fun `test Match union bound 1`() {
    doTestByText("""
      from typing import assert_type, Callable
  
      def f2[T](arg: str | Callable[[T], None]) -> T:
        pass
  
      def callback(p: int) : ...
  
      r = f2(callback)
      assert_type(r, int)
      """)
  }

  fun `test Match union bound 2`() {
    doTestByText("""
      from typing import Any, assert_type, Callable
  
      def f2[T: int | Callable[[str], int]](arg: T) -> T:
        pass
  
      my_lambda = lambda s,/: 42
      r = f2(my_lambda)
      assert_type(r, Callable[[Any], int]) # subtype allowed
      """)
  }

  fun `test Match union bound 3`() {
    doTestByText("""
      from typing import assert_type, Callable
  
      def f2[T: int | Callable[[str], int]](arg: T) -> T:
        pass
  
      def callback(p: str, /) -> int : ...
  
      r = f2(callback)
      assert_type(r, Callable[[str], int])
      """)
  }

  fun `test Match union bound 4`() {
    doTestByText("""
      from typing import Any, assert_type, Callable
  
      def f2[T=bool](arg: T | Callable[[str], int]) -> T:
        pass
  
      r = f2(lambda s: 42)
      assert_type(r, bool)
      """)
  }

  fun `test Match constraint`() {
    doTestByText("""
      from typing import Any, assert_type, Callable
  
      def f2[T: (int, Callable[[str], None])](arg: T) -> T:
        pass
  
      r = f2(lambda s: assert_type(s, str))
      assert_type(r, Callable[[str], None])
      """)
  }

  fun `test Nested type variables 4a`() {
    doTestByText("""
      from typing import assert_type
  
      class A: ...
      class B(A): ...
      class Pair[U, V]:
        def __init__(self, first: U, second: V):
          self.first = first
          self.second = second
  
      def merge[U](pair: Pair[U, U]) -> U:
        return None
  
      def pipe[U](arg: U) -> U:
        return None
  
      r4a = merge(pipe(Pair(B(), A()))) # note: same result without call to 'pipe'
      assert_type(r4a, A | B)
      """)
  }

  fun `test Nested type variables 4b`() {
    doTestByText("""
      from typing import assert_type
  
      class A: ...
      class B(A): ...
      class Pair[U, V]:
        def __init__(self, first: U, second: V):
          self.first = first
          self.second = second
  
      def merge[U](pair: Pair[U, U]) -> U:
        return None

      r4b = merge(Pair("s", 1))
      assert_type(r4b, str | int)
      """)
  }

  fun `test Nested type variables 4c`() {
    doTestByText("""
      from typing import assert_type
  
      class A: ...
      class B(A): ...
      class Pair[U, V]:
        def __init__(self, first: U, second: V):
          self.first = first
          self.second = second
  
      def merge2[U, V](pair1: Pair[U, V], pair2: Pair[U, V]) -> U:
        return None
  
      r4c = merge2(Pair(B(), B()), Pair(B(), A()))
      assert_type(r4c, B)
      """)
  }

  fun `test Generic and self`() {
    doTestByText("""
      from typing import TypeVar, assert_type
  
      class A:
          def copy[T](self: T) -> T:
              return self
  
      assert_type(A.copy(A()), A)
      """)
  }

  fun `test Deeply nested generics`() {
    doTestByText("""
      from typing import assert_type
  
      def f[T](x: list[list[list[T]]]) -> T:
          ...
  
      res = f([[[1]]])
      assert_type(res, int)
      """)
  }

  fun `test Type var constraints vs bound`() {
    doTestByText("""
      from typing import assert_type
  
      def f_constrained[T: (int, str)](x: T) -> T:
          return x
      
      def f_bound[T: int](x: T) -> T:
          return x

      r1 = f_constrained(1)
      assert_type(r1, int)
  
      r2 = f_constrained("s")
      assert_type(r2, str)
  
      class MyInt(int): ...
      r3 = f_bound(MyInt())
      assert_type(r3, MyInt)
  
      r4 = f_constrained(MyInt())
      assert_type(r4, int)
      """)
  }

  fun `test Handle inferred intersections 1`() {
    doTestByText("""
      from typing import Callable, TypeVar, assert_type
  
      class A: ...          
      class B: ...
      class C(B, A): ...
  
      T = TypeVar('T', bound=A)
  
      def func(c: Callable[[T], None])->T:
          pass
  
      def accepts_str(x: B) -> None:
          pass
  
      res: C = func(accepts_str)
      assert_type(res, C)
      """)
  }

  fun `test Handle inferred intersections 2`() {
    doTestByText("""
      from typing import Callable, TypeVar, assert_type, Never
  
      class A: ...          
      class B: ...
      # class C(B, A): ... # no C given
  
      T = TypeVar('T', bound=A)
  
      def func(c: Callable[[T], None])->T:
          pass
  
      def accepts_str(x: B) -> None:
          pass
  
      res = func(<warning descr="Expected type '(T ≤: A) -> None', got '(x: B) -> None' instead">accepts_str</warning>)
      assert_type(res, Any)
      """)
  }

  fun `test Infer constrained type`() {
    doTestByText("""
      from typing import TypeVar, Generic, assert_type
  
      class A: ...
      class B: ...
      class C(B): ...
  
      T = TypeVar("T", A, B, contravariant=True)
  
      class Box(Generic[T]):
          def __init__(self, t: T):
              pass
  
      a = Box(C())
      assert_type(a, Box[B])
      """)
  }

  fun `test Handle raw generic type`() {
    doTestByText("""
      class A: ...
      class Box[E:A]:
          def __init__(self, e: E): ...
      
      def foo[U:Box](u:U) -> U:
          pass
      
      foo(Box(<warning descr="Expected type 'E ≤: A', got 'int' instead">1</warning>))
      """)
  }


  @TestFor(issues = ["PY-86098"])
  fun `test PY-86098`() {
    doTestByText("""
      class A[T: object]:
          def __init__(self, value: T): ...
      
      a1 = A(1)
      assert_type(a1, A[int])
      """)
  }
}