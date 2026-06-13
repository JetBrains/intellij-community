// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

class PyExpectedVarianceJudgmentTest : PyCodeInsightTestCase() {

  @Test
  fun `Generic super class expects bivariant type parameters`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T")
    class C(Generic[T]):
    #               └ EXPECTED_VARIANCE BIVARIANT
        pass
    """.trimIndent())

  @Test
  fun `Generic super class expects bivariant type parameters co`() = test("""
    from typing import TypeVar, Generic
    T1 = TypeVar("T1", covariant=True)
    class Box(Generic[T1]):
    #                 └ EXPECTED_VARIANCE BIVARIANT
        pass
    """.trimIndent())

  @Test
  fun `Generic super class expects bivariant type parameters contra`() = test("""
    from typing import TypeVar, Generic
    T1 = TypeVar("T1", contravariant=True)
    class Box(Generic[T1]):
    #                 └ EXPECTED_VARIANCE BIVARIANT
        pass
    """.trimIndent())

  @Test
  fun `Protocol super class expects bivariant type parameters`() = test("""
    from typing import TypeVar, Protocol
    T = TypeVar("T")
    class C(Protocol[T]):
    #                └ EXPECTED_VARIANCE BIVARIANT
    #                └ WARNING This type variable is effectively covariant in this protocol, so it cannot be invariant here
        pass
    """.trimIndent())

  @Test
  fun `Generic class attribute`() = test("""
    class A[T]:
        attr: T # attribute
    #         └ EXPECTED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `Generic class attribute callable parameter`() = test("""
    from typing import Callable
    class A[T]:
        attr: Callable[[T], None]
    #                   └ EXPECTED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `Generic class attribute callable return`() = test("""
    from typing import Callable
    class A[T]:
        attr: Callable[[], T] # attribute
    #                      └ EXPECTED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `Generic class readonly attribute`() = test("""
    from typing import ReadOnly, TypedDict
    class A[T](TypedDict):
        attr: ReadOnly[T] # attribute
    #                  └ EXPECTED_VARIANCE COVARIANT
    """.trimIndent())

  @Test
  fun `Generic class final attribute`() = test("""
    from typing import Final
    class A[T]:
        def __init__(self): self.attr = None
        attr: Final[T]
    #               └ EXPECTED_VARIANCE COVARIANT
    """.trimIndent())

  @Test
  fun `Generic class final attribute callable parameter and return`() = test("""
    from typing import Final, Callable
    class A[T, R]:
        def __init__(self): self.attr = lambda a: None
        attr: Final[Callable[[T], R]]
    #                         │   └ EXPECTED_VARIANCE COVARIANT
    #                         └ EXPECTED_VARIANCE CONTRAVARIANT
    """.trimIndent())

  @Test
  fun `Generic class final attribute callable concatenate parameter`() = test("""
    from typing import Callable, Concatenate, Final
    class A[T, **P]:
        def __init__(self): self.attr = lambda a: None
        attr: Final[Callable[Concatenate[T, P], None]]
    #                                    └ EXPECTED_VARIANCE CONTRAVARIANT
    """.trimIndent())

  @Test
  fun `Generic class method parameter and return`() = test("""
    class A[T, R]:
        def method(self, t: T) -> R: pass
    #                       │     └ EXPECTED_VARIANCE COVARIANT
    #                       └ EXPECTED_VARIANCE CONTRAVARIANT
    """.trimIndent())

  @Test
  fun `Generic class nested covariant`() = test("""
    from typing import Iterable
    class A[T]:
        def method(self) -> Iterable[T]: pass
    #                                └ EXPECTED_VARIANCE COVARIANT
    """.trimIndent())

  @Test
  fun `Generic class method parameter nesting callable parameter`() = test("""
    from typing import Callable
    class A[T]:
        def method(self, arg: Callable[[T], None]): pass
    #                                   └ EXPECTED_VARIANCE COVARIANT
    """.trimIndent())

  @Test
  fun `Generic class method parameter nesting callable concatenate parameter`() = test("""
    from typing import Callable, Concatenate
    class A[T, **P]:
        def method(self, arg: Callable[Concatenate[T, P], None]): ...
    #                                              └ EXPECTED_VARIANCE COVARIANT
    """.trimIndent())

  @Test
  fun `Generic class method parameter nesting callable parameter and return`() = test("""
    from typing import Callable
    class A[T, R]:
        def method(self, arg: Callable[[T], R]): pass
    #                                   │   └ EXPECTED_VARIANCE CONTRAVARIANT
    #                                   └ EXPECTED_VARIANCE COVARIANT
    """.trimIndent())

  @Test
  fun `Generic class method return nesting callable concatenate parameter`() = test("""
    from typing import Callable, Concatenate
    class A[T, **P]:
        def f2(self, t: T) -> Callable[Concatenate[T, P], None]: ...
    #                                              └ EXPECTED_VARIANCE CONTRAVARIANT
    """.trimIndent())

  @Test
  fun `Generic class method return nesting callable return`() = test("""
    from typing import Callable
    class A[T, R]:
        def method(self) -> Callable[[T], R]: pass
    #                                 │   └ EXPECTED_VARIANCE COVARIANT
    #                                 └ EXPECTED_VARIANCE CONTRAVARIANT
    """.trimIndent())

  @Test
  fun `Generic class type argument legacy syntax 1`() = test("""
    from typing import TypeVar, Generic
    T1 = TypeVar("T1", infer_variance=False)
    class Box(Generic[T1]):
        pass
    T2 = TypeVar('T2', contravariant=True)
    class ReadOnlyBox(Box[T2], Generic[T2]):
    #                     └ EXPECTED_VARIANCE INVARIANT # not affected by warning below
    #                     ^^ WARNING A contravariant type variable cannot be used in this invariant position
        pass
    """.trimIndent())

  @Test
  fun `Generic class type argument legacy syntax 1a`() = test("""
    from typing import TypeVar, Generic
    T1 = TypeVar("T1", covariant=True)
    class Box(Generic[T1]):
        pass
    T2 = TypeVar('T2', contravariant=True)
    class ReadOnlyBox(Box[T2], Generic[T2]):
    #                     └ EXPECTED_VARIANCE COVARIANT # not affected by warning below
    #                     ^^ WARNING A contravariant type variable cannot be used in this covariant position
        pass
    """.trimIndent())

  @Test
  fun `Generic class type argument legacy syntax 1b`() = test("""
    from typing import TypeVar, Generic
    T1 = TypeVar("T1", contravariant=False)
    class Box(Generic[T1]):
        pass
    T2 = TypeVar('T2', covariant=True)
    class ReadOnlyBox(Box[T2], Generic[T2]):
    #                     └ EXPECTED_VARIANCE INVARIANT # not affected by warning below
    #                     ^^ WARNING A covariant type variable cannot be used in this invariant position
        pass
    """.trimIndent())

  @Test
  fun `Generic class type argument legacy syntax 2a`() = test("""
    from typing import TypeVar, Generic
    T1 = TypeVar("T1", infer_variance=False)
    T2 = TypeVar("T2", infer_variance=False)
    class Box(Generic[T1, T2]):
        pass
    
    T3 = TypeVar("T3", contravariant=True)
    T4 = TypeVar("T4", contravariant=True)
    class ReadOnlyBox(Box[T3, T4]): # ISSUES *
    #                     │   └ EXPECTED_VARIANCE INVARIANT
    #                     └ EXPECTED_VARIANCE INVARIANT
        pass
    """.trimIndent())

  @Test
  fun `Generic class type argument PEP695 syntax`() = test("""
    from typing import TypeVar, Generic
    class Box[T1]:
        pass
    T2 = TypeVar('T2', contravariant=True)
    class ReadOnlyBox(Box[T2], Generic[T2]):
    #                     └ EXPECTED_VARIANCE BIVARIANT
        pass
    """.trimIndent())

  @Test
  fun `Generic class type argument PEP695 syntax 2a`() = test("""
    from typing import TypeVar, Generic
    class Box[T1, T2]:
        pass
    
    T3 = TypeVar("T3", contravariant=True)
    T4 = TypeVar("T4", contravariant=True)
    class ReadOnlyBox(Box[T3, T4]):
    #                     │   └ EXPECTED_VARIANCE BIVARIANT
    #                     └ EXPECTED_VARIANCE BIVARIANT
        pass
    """.trimIndent())

  @Test
  fun `Nested generic classes invariant`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T")
    T_co = TypeVar("T_co", covariant=True)
    T_contra = TypeVar("T_contra", contravariant=True)
    class Co(Generic[T_co]):
        pass
    class Contra(Generic[T_contra]):
        pass
    class A(Contra[Co[T]]):
    #                 └ EXPECTED_VARIANCE CONTRAVARIANT
        pass
    """.trimIndent())

  @Test
  fun `Nested generic classes covariant`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T")
    T_co = TypeVar("T_co", covariant=True)
    T_contra = TypeVar("T_contra", contravariant=True)
    class Co(Generic[T_co]):
        pass
    class Contra(Generic[T_contra]):
        pass
    class A(Co[Co[T]]):
    #             └ EXPECTED_VARIANCE COVARIANT
        pass
    """.trimIndent())

  @Test
  fun `Nested generic classes contravariant`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T")
    T_co = TypeVar("T_co", covariant=True)
    T_contra = TypeVar("T_contra", contravariant=True)
    class Co(Generic[T_co]):
        pass
    class Contra(Generic[T_contra]):
        pass
    class A(Contra[Contra[T]]):
    #                     └ EXPECTED_VARIANCE COVARIANT
        pass
    """.trimIndent())

  @Test
  fun `Frozen attribute`() = test("""
    from dataclasses import dataclass
    @dataclass(frozen=True)
    class A[T]:
        attr: T  # read-only
    #         └ EXPECTED_VARIANCE COVARIANT
    """.trimIndent())

  @Test
  @TestFor(issues=["PY-90269"])
  fun `Frozen attribute via dataclass_transform frozen_default`() = test("""
    from typing import dataclass_transform
    
    @dataclass_transform(frozen_default=True)
    def model(cls): ...
    
    @model
    class A[T]:
        attr: T  # read-only
    #         └ EXPECTED_VARIANCE COVARIANT
    """.trimIndent())

  @Test
  @TestFor(issues=["PY-90269"])
  fun `Mutable attribute via dataclass_transform frozen_default overridden`() = test("""
    from typing import dataclass_transform, Callable
    
    @dataclass_transform(frozen_default=True)
    def model(frozen: bool = True) -> Callable: ...
    
    @model(frozen=False)
    class A[T]:
        attr: T  # mutable
    #         └ EXPECTED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `String literal type`() = test("""
    from dataclasses import dataclass
    @dataclass(frozen=True)
    class A[T]:
        attr: "T"  # read-only
    #          └ EXPECTED_VARIANCE COVARIANT
    """.trimIndent())

  @Test
  fun `String literal type at return inside callable`() = test("""
    from typing import Callable
    class A[T]:
        def f(self, t: Callable[["T"],None]) : ...
    #                             └ EXPECTED_VARIANCE COVARIANT
    """.trimIndent())

  @Test
  fun `String literal type at function parameter`() = test("""
    from typing import Callable
    class A[T]:
        def f(self, t: "Callable[[T], None]") : ...
    #                             └ EXPECTED_VARIANCE COVARIANT
    """.trimIndent())

  @Test
  fun `Type alias use for generic class invariant`() = test("""
    from typing import TypeVar, Generic, TypeAlias
    T1 = TypeVar("T1")
    class Box(Generic[T1]): ...
    Box_TA: TypeAlias = Box[T1]
    #                       └ EXPECTED_VARIANCE NULL FIXME INVARIANT
    my_box: Box_TA[int]
    #              └ EXPECTED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `Type alias use for generic class covariant`() = test("""
    from typing import Generic, TypeVar, TypeAlias
    T_co = TypeVar("T_co", covariant=True)
    class ClassA(Generic[T_co]): ...
    
    T = TypeVar("T")
    A_Alias_1: TypeAlias = ClassA[T]
    #                             └ EXPECTED_VARIANCE NULL FIXME COVARIANT
    
    obj: A_Alias_1[int] #
    #              └ EXPECTED_VARIANCE COVARIANT
    """.trimIndent())

  // Expect null to avoid variance compatibility inspection check

  @Test
  fun `Type argument of self type`() = test("""
    class K[T]:
        def m1(self: "K[T]", x: T) -> None: ...
    #                   └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Generic class dunder init special case`() = test("""
    class A[T]:
        def __init__(self, value: T): pass
    #                             └ EXPECTED_VARIANCE NULL # actually bivariant
    """.trimIndent())

  @Test
  fun `Generic class dunder new special case`() = test("""
    class A[T]:
        def __new__(self, value: T): pass
    #                            └ EXPECTED_VARIANCE NULL # actually bivariant
    """.trimIndent())

  @Test
  fun `Generic class dunder init safety`() = test("""
    class A[T]:
        def __init__(self, value: T) -> None : pass
    #                                    └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Private attributes are ignored`() = test("""
    class A[T]:
        __t: T  # private
    #        └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Private methods are ignored`() = test("""
    class A[T]:
        def __foo(self, t:T) -> T: pass  # private
    #                     └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Protected attributes are ignored`() = test("""
    class A[T]:
        _t: T  # protected
    #       └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Protected methods are ignored`() = test("""
    class A[T]:
        def _foo(self, t:T) -> T: pass  # private
    #                    └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when bound to function return 1`() = test("""
    from typing import TypeVar
    T = TypeVar("T", covariant=True)
    def fn() -> T: pass
    #           └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when bound to function return 2`() = test("""
    def fn[T]() -> T: pass
    #              └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when bound to function param 1`() = test("""
    from typing import TypeVar
    T = TypeVar("T", covariant=True)
    def fn(t: T): pass
    #         └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when bound to function param 2`() = test("""
    def fn[T](t: T): pass
    #            └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when bound to function parameter nesting callable parameter`() = test("""
    from typing import Callable
    def fn[T](t: Callable[[T], None]): pass
    #                      └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when bound to function parameter nesting callable return`() = test("""
    from typing import Callable
    def fn[T](t: Callable[[], T]): pass
    #                         └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when bound to function return nesting callable parameter`() = test("""
    from typing import Callable
    def fn[T]() -> Callable[[T], None]: pass
    #                        └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when bound to function return nesting callable return`() = test("""
    from typing import Callable
    def fn[T]() -> Callable[[], T]: pass
    #                           └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when bound to function generic parameter`() = test("""
    from typing import TypeVar
    B_co = TypeVar("B_co", covariant=True)
    def func(x: list[B_co]) -> B_co:
    #                └ EXPECTED_VARIANCE NULL
        ...
    """.trimIndent())

  @Test
  fun `Null when in function`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", covariant=True)
    def fn() -> T: pass
    #           └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when in unbound instance function`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", covariant=True)
    class C(Generic[T]):
        def fn() -> T: pass
    #               └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when in class function`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", covariant=True)
    class C(Generic[T]):
        @classmethod
        def fn(cls) -> T: pass
    #                  └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when in static function`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", covariant=True)
    class C(Generic[T]):
        @staticmethod
        def fn() -> T: pass
    #               └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when pass`() = test("""
    class C[T]:
        def method(self) -> T: pass
    #                          └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when default parameter value`() = test("""
    class C[T]:
        def method(self, a = 1) -> T: pass
    #                        └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when literal expression`() = test("""
    class C[T]:
        def method(self, a: int) -> T:
            return a + 2
    #                  └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when ref in some expression`() = test("""
    class C[T]:
        def method(self, a: int) -> T:
            return a + 2
    #              └ EXPECTED_VARIANCE NULL
    """.trimIndent())

  @Test
  fun `Null when ref in attr initializer`() = test("""
    class A[T]:
        attr: T = None
    #             └ EXPECTED_VARIANCE NULL
    """.trimIndent())

}
