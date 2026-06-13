// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test


class PyInferredVarianceJudgmentTest : PyCodeInsightTestCase() {


  // Variance is declared as non-infer variance

  @Test
  fun `Generic function with type var declared invariant`() = test("""
    from typing import TypeVar
    T = TypeVar("T")
    def fn(t: T): pass
    #         └ INFERRED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `Generic function with type var declared covariant`() = test("""
    from typing import TypeVar
    T = TypeVar("T", covariant=True)
    def fn(t: T): pass
    #         └ INFERRED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `Generic function with declared contravariant`() = test("""
    from typing import TypeVar
    T = TypeVar("T", contravariant=True)
    def fn() -> T: pass
    #           └ INFERRED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `Generic class unused type var declared invariant`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T")
    class A(Generic[T]):
    #               └ INFERRED_VARIANCE INVARIANT
        def method(self): pass
    """.trimIndent())

  @Test
  fun `Generic class unused type var declared covariant`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", covariant=True)
    class A(Generic[T]):
    #               └ INFERRED_VARIANCE COVARIANT
        def method(self): pass
    """.trimIndent())

  @Test
  fun `Generic class unused type var declared contravariant`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", contravariant=True)
    class A(Generic[T]):
    #               └ INFERRED_VARIANCE CONTRAVARIANT
        def method(self): pass
    """.trimIndent())

  // Inferring variance is necessary: Functions have no impact

  @Test
  fun `Generic function unused`() = test("""
    def fn[T](): pass
    #      └ INFERRED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `Generic function return type`() = test("""
    def fn[T]() -> T: pass
    #      └ INFERRED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `Generic function parameter`() = test("""
    def fn[T](t: T): pass
    #      └ INFERRED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `Generic function parameter nesting callable parameter`() = test("""
    from typing import Callable
    def fn[T](t: Callable[[T], None]): pass
    #      └ INFERRED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `Generic function parameter nesting callable return`() = test("""
    from typing import Callable
    def fn[T](t: Callable[[], T]): pass
    #      └ INFERRED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `Generic function return nesting callable parameter`() = test("""
    from typing import Callable
    def fn[T]() -> Callable[[T], None]: pass
    #      └ INFERRED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `Generic function return nesting callable return`() = test("""
    from typing import Callable
    def fn[T]() -> Callable[[], T]: pass
    #      └ INFERRED_VARIANCE INVARIANT
    """.trimIndent())

  // Inferring variance is necessary

  @Test
  fun `Generic class unused`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE BIVARIANT
        def method(self): pass
    """.trimIndent())

  @Test
  fun `Generic sub class unused`() = test("""
    class A[S]:
        ...
    class B[T](A[T]):
    #       └ INFERRED_VARIANCE BIVARIANT
        def method(self): pass
    """.trimIndent())

  @Test
  fun `Generic class unused TypeVar syntax`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", infer_variance=True)
    class A(Generic[T]):
    #               └ INFERRED_VARIANCE BIVARIANT
        def method(self): pass
    """.trimIndent())

  @Test
  fun `Generic protocol class unused TypeVar syntax`() = test("""
    from typing import TypeVar, Protocol
    T = TypeVar("T", infer_variance=True)
    class A(Protocol[T]):
    #                └ INFERRED_VARIANCE BIVARIANT
    #                └ WARNING This type variable is effectively covariant in this protocol, so it cannot be bivariant here FIXME
        def method(self): pass
    """.trimIndent())

  @Test
  fun `Generic class attribute`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE INVARIANT
        attr: T
    """.trimIndent())

  @Test
  fun `Generic class attribute callable parameter`() = test("""
    from typing import Callable
    class A[T]:
    #       └ INFERRED_VARIANCE INVARIANT
        attr: Callable[[T], None]
    """.trimIndent())

  @Test
  fun `Generic class attribute callable return`() = test("""
    from typing import Callable
    class A[T]:
    #       └ INFERRED_VARIANCE INVARIANT
        attr: Callable[[], T]
    """.trimIndent())

  @Test
  fun `Generic class readonly attribute`() = test("""
    from typing import ReadOnly, TypedDict
    class A[T](TypedDict):
    #       └ INFERRED_VARIANCE COVARIANT
        attr: ReadOnly[T] # attribute
    """.trimIndent())

  @Test
  fun `Generic class final attribute`() = test("""
    from typing import Final
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        def __init__(self): self.attr = None
        attr: Final[T]
    """.trimIndent())

  @Test
  fun `Generic class final attribute in quotes`() = test("""
    from typing import Final
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        attr: Final["T"] # ISSUES *
    """.trimIndent())

  @Test
  fun `Generic class final attribute callable parameter`() = test("""
    from typing import Final, Callable
    class A[T]:
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        def __init__(self): self.attr = lambda a: None
        attr: Final[Callable[[T], None]]
    """.trimIndent())

  @Test
  fun `Generic class final attribute callable concatenate parameter`() = test("""
    from typing import Callable, Concatenate, Final
    class A[T, **P]:
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        def __init__(self): self.attr = lambda a: None
        attr: Final[Callable[Concatenate[T, P], None]]
    """.trimIndent())

  @Test
  fun `Generic class final attribute callable return`() = test("""
    from typing import Final, Callable
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        def __init__(self): self.attr = lambda a: None
        attr: Final[Callable[[], T]]
    """.trimIndent())

  @Test
  fun `Generic class method parameter with inherited attribute`() = test("""
    class A[AT]:
        attr: AT
    
    class B[BT](A[BT]):
    #       └ INFERRED_VARIANCE INVARIANT
        def method(self, t: BT): pass
    """.trimIndent())

  @Test
  fun `Generic class method return with inherited attribute`() = test("""
    class A[AT]:
        attr: AT
    
    class B[BT](A[BT]):
    #       └ INFERRED_VARIANCE INVARIANT
        def method(self) -> BT: pass
    """.trimIndent())

  @Test
  fun `Generic class with inherited attribute`() = test("""
    class A[AT]:
        attr: AT # AT is inferred to be invariant
    
    class B[BT](A[BT]):
    #       └ INFERRED_VARIANCE INVARIANT
        pass
    """.trimIndent())

  @Test
  fun `Generic class method return with inherited method parameter`() = test("""
    class A[AT]:
        def method(self, t: AT): pass # AT is inferred to be contravariant
    
    class B[BT](A[BT]):
    #       └ INFERRED_VARIANCE INVARIANT
        def method(self) -> BT: pass
    """.trimIndent())

  @Test
  fun `Generic class attribute with method parameter`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE INVARIANT
        attr: T
        def method(self, t: T): pass
    """.trimIndent())

  @Test
  fun `Generic class attribute with method return`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE INVARIANT
        attr: T
        def method(self) -> T: pass
    """.trimIndent())

  @Test
  fun `Generic class return type`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        def method(self) -> T: pass
    """.trimIndent())

  @Test
  fun `Generic class parameter`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        def method(self, arg: T): pass
    """.trimIndent())

  @Test
  fun `Generic class invariant`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE INVARIANT
        def method(self, arg: T) -> T: pass
    """.trimIndent())

  @Test
  fun `Generic class nested covariant`() = test("""
    from typing import Iterable
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        def method(self) -> Iterable[T]: pass # Covariant in Covariant -> Covariant
    """.trimIndent())

  @Test
  fun `Generic class method parameter nesting callable parameter`() = test("""
    from typing import Callable
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        def method(self, arg: Callable[[T], None]): pass # Contravariant in Contravariant -> Covariant
    """.trimIndent())

  @Test
  fun `Generic class method parameter nesting callable concatenate parameter`() = test("""
    from typing import Callable, Concatenate
    class A[T, **P]:
    #       └ INFERRED_VARIANCE COVARIANT
        def method(self, arg: Callable[Concatenate[T, P], None]): ...
    """.trimIndent())

  @Test
  fun `Generic class method parameter nesting callable return`() = test("""
    from typing import Callable
    class A[T]:
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        def method(self, arg: Callable[[], T]): pass # Covariant in Contravariant -> Contravariant
    """.trimIndent())

  @Test
  fun `Generic class method return nesting callable parameter`() = test("""
    from typing import Callable
    class A[T]:
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        def method(self) -> Callable[[T], None]: pass # Contravariant in Covariant -> Contravariant
    """.trimIndent())

  @Test
  fun `Generic class method return nesting callable concatenate parameter`() = test("""
    from typing import Callable, Concatenate
    class A[T, **P]:
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        def f2(self, t: T) -> Callable[Concatenate[T, P], None]: ...
    """.trimIndent())

  @Test
  fun `Generic class method return nesting callable return`() = test("""
    from typing import Callable
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        def method(self) -> Callable[[], T]: pass # Covariant in Covariant -> Covariant
    """.trimIndent())

  @Test
  fun `Generic class method return nesting default variance class`() = test("""
    class Box[U]:
        def method(self): pass # U is inferred to have default variance
    
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        def method(self) -> Box[T]: pass # Covariant in Covariant -> Covariant
    """.trimIndent())

  @Test
  fun `Generic class method return nesting covariant class`() = test("""
    class Box[U]:
        def method_box(self) -> U: pass # U is inferred to be covariant
    
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        def method(self) -> Box[T]: pass # Covariant in Covariant -> Covariant
    """.trimIndent())

  @Test
  fun `Generic class method parameter nesting covariant class`() = test("""
    class Box[U]:
        def method_box(self) -> U: pass # U is inferred to be covariant
    
    class A[T]:
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        def method(self, p: Box[T]): pass # Covariant in Contravariant -> Contravariant
    """.trimIndent())

  @Test
  fun `Generic class method return nesting contravariant class`() = test("""
    class Box[U]:
        def method_box(self, arg: U): pass # U is inferred to be contravariant
    
    class A[T]:
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        def method(self) -> Box[T]: pass # Contravariant in Covariant -> Contravariant
    """.trimIndent())

  @Test
  fun `Generic class method parameter nesting contravariant class`() = test("""
    class Box[U]:
        def method_box(self, arg: U): pass # U is inferred to be contravariant
    
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        def method(self, p: Box[T]): pass # Contravariant in Contravariant -> Covariant
    """.trimIndent())

  @Test
  fun `Generic class method parameter nesting invariant class`() = test("""
    class Box[U]:
        attr: U # U is inferred to be invariant
    
    class A[T]:
    #       └ INFERRED_VARIANCE INVARIANT
        def method(self, p: Box[T]): pass # Invariant in Contravariant -> Invariant
    """.trimIndent())

  @Test
  fun `Generic class method return nesting invariant class`() = test("""
    class Box[U]:
        attr: U # U is inferred to be invariant
    
    class A[T]:
    #       └ INFERRED_VARIANCE INVARIANT
        def method(self) -> Box[T]: pass # Invariant in Covariant -> Invariant
    """.trimIndent())

  @Test
  fun `Nesting contravariant in invariant`() = test("""
    class Box[U]:
        def method_box(self, arg: U) -> U: pass # U is inferred to be invariant
    
    class A[T]:
    #       └ INFERRED_VARIANCE INVARIANT
        def method(self) -> Box[T]: pass
    """.trimIndent())

  @Test
  fun `Nesting contravariant in covariant`() = test("""
    class Box[U]:
        def method_box(self, arg: U): pass # Contravariant
    
    class User[T]:
    #          └ INFERRED_VARIANCE CONTRAVARIANT
        def method(self) -> Box[T]: pass
    """.trimIndent())

  @Test
  fun `Generic instance function`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", infer_variance=True)
    class Box(Generic[T]):
    #                 └ INFERRED_VARIANCE CONTRAVARIANT
        def method(self, t: T): pass
    """.trimIndent())

  @Test
  fun `Multiple generic methods on same type var C1`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", infer_variance=True)
    class C1(Generic[T]):# C1
    #                └ INFERRED_VARIANCE CONTRAVARIANT
        def method1(self, t: T): pass
    class C2(Generic[T]):# C2
        def method2(self) -> T: pass
    """.trimIndent())

  @Test
  fun `Multiple generic methods on same type var C2`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", infer_variance=True)
    class C1(Generic[T]):#C1
        def method1(self, t: T): pass
    class C2(Generic[T]):#C2
    #                └ INFERRED_VARIANCE COVARIANT
        def method2(self) -> T: pass
    """.trimIndent())

  @Test
  fun `multiple generic methods on same type var with subtype`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", infer_variance=True)
    class C1(Generic[T]):# C1
        def method1(self, t: T): pass
    class C2(C1[T], Generic[T]):# C2
    #                       └ INFERRED_VARIANCE INVARIANT
        def method2(self) -> T: pass
    """.trimIndent())

  @Test
  fun `Default variance class with covariant subtype`() = test("""
    class Box[U]:
        def method(self): pass # U is inferred to have default variance
    
    class A[T](Box[T]):
    #       └ INFERRED_VARIANCE COVARIANT
        def method(self) -> T: pass
    """.trimIndent())

  @Test
  fun `Default variance class with contravariant subtype`() = test("""
    class Box[U]:
        def method(self): pass # U is inferred to have default variance
    
    class A[T](Box[T]):
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        def method(self, t: T): pass
    """.trimIndent())

  @Test
  fun `Generic method and unrelated function on same type var`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", infer_variance=True)
    class C1(Generic[T]):
    #                └ INFERRED_VARIANCE CONTRAVARIANT
        def method1(self, t: T): pass
    
    def fn2() -> T: # does not change the inferred variance
        pass
    """.trimIndent())

  @Test
  fun `Generic instance functions and unbound instance function on same type var`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", infer_variance=True)
    class C(Generic[T]):
    #               └ INFERRED_VARIANCE CONTRAVARIANT
        def method1(self, t: T): pass
        def fn() -> T: pass # does not change the inferred variance
    """.trimIndent())

  @Test
  fun `Generic instance functions and class method on same type var`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", infer_variance=True)
    class C(Generic[T]):
    #               └ INFERRED_VARIANCE CONTRAVARIANT
        def method1(self, t: T): pass
        @classmethod
        def fn(cls) -> T: pass # does not change the inferred variance
    """.trimIndent())

  @Test
  fun `Generic instance functions and static method on same type var`() = test("""
    from typing import TypeVar, Generic
    T = TypeVar("T", infer_variance=True)
    class C(Generic[T]):
    #               └ INFERRED_VARIANCE CONTRAVARIANT
        def method1(self, t: T): pass
        @staticmethod
        def fn() -> T: pass # does not change the inferred variance
    """.trimIndent())

  @Test
  fun `Generic instance functions and unbound instance function on same type var using new syntax`() = test("""
    class D[U]:
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        def method1(self, u: U): pass
        @staticmethod
        def fn() -> U: pass # does not change the inferred variance
    """.trimIndent())

  @Test
  fun `Generic instance functions and class method on same type var using new syntax`() = test("""
    class D[U]:
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        def method1(self, u: U): pass
        @classmethod
        def fn(cls) -> U: pass # does not change the inferred variance
    """.trimIndent())

  @Test
  fun `Generic instance functions and static method on same type var using new syntax`() = test("""
    class D[U]:
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        def method1(self, u: U): pass
        @staticmethod
        def fn() -> U: pass # does not change the inferred variance
    """.trimIndent())

  @Test
  fun `Covariant method and __init__ method`() = test("""
    class A[T]:  # covariant
    #       └ INFERRED_VARIANCE COVARIANT
        def __init__(self, t: T): ...
        
        def f(self) -> T: ...
    """.trimIndent())

  @Test
  fun `Contravariant method and __init__ method`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        def __init__(self, t: T): ...
        
        def f(self, T: T): ...
    """.trimIndent())

  @Test
  fun `Covariant method and __new__ method`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        def __new__(self, t: T): ...
        
        def f(self) -> T: ...
    """.trimIndent())

  @Test
  fun `Contravariant method and __new__ method`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        def __new__(self, t: T): ...
        
        def f(self, T: T): ...
    """.trimIndent())

  @Test
  fun `Private attributes are ignored`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE BIVARIANT
        __t: T  # private
    """.trimIndent())

  @Test
  fun `Private methods are ignored`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE BIVARIANT
        def __foo(self, t:T) -> T: pass  # private
    """.trimIndent())

  @Test
  fun `Protected attributes are ignored`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE BIVARIANT
        _t: T  # protected
    """.trimIndent())

  @Test
  fun `Protected methods are ignored`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE BIVARIANT
        def _foo(self, t:T) -> T: pass  # private
    """.trimIndent())

  @Test
  fun `Sunder members are not ignored`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE INVARIANT
        _attr_ : T  # sunder attribute
        def _method_(self, t:T) -> T: pass  # sunder method
    """.trimIndent())

  @Test
  fun `Dunder members are not ignored`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE INVARIANT
        __attr__ : T  # dunder attribute
        def __method__(self, t:T) -> T: pass  # dunder method
    """.trimIndent())

  // Note that body information availability depends on the capability of the context:
  // For local files it is available, but for other files it is not since they rely on stub information.
  // That behavior would make variance inference flaky.
  @Test
  fun `Implicit generic attributes are ignored`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE BIVARIANT
        def __init__(self, t: T):
            self.t = t # introduction of public attribute without class declaration
    """.trimIndent())

  @Test
  fun `Implicit generic final class attributes`() = test("""
    from typing import Final
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        def __init__(self, t: T):
            self.t : Final[T] = t
    """.trimIndent())

  @Test
  fun `Implicit generic final class attributes multi file setup`() = test("""
    from lib import A
    class B[S](A[S]):
    #       └ INFERRED_VARIANCE COVARIANT
        def __init__(self):
            ...
    """.trimIndent(),
                      "lib.py" to """
            from typing import Final
            class A[T]:
                def __init__(self, t: T):
                    tmp = t
                    self.t : Final[T] = t
            """)

  @Test
  fun `Implicit generic protected class attributes`() = test("""
    from typing import Final
    class A[T]:
    #       └ INFERRED_VARIANCE BIVARIANT
        def __init__(self, t: T):
            self._t = t
    """.trimIndent())

  @Test
  fun `Implicit generic private class attributes`() = test("""
    from typing import Final
    class A[T]:
    #       └ INFERRED_VARIANCE BIVARIANT
        def __init__(self, t: T):
            self.__t = t
    """.trimIndent())

  // Note that inferred return types depend on the capability of the context:
  // For local files it can be inferred, but for other files it will be inferred to Any.
  // That behavior would make variance inference flaky.
  @Test
  fun `Implicit generic return types are ignored`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE BIVARIANT
        __t: T  # no effect on inferred variance
        def foo(self):
            return self.__t # returned type is T
    """.trimIndent())

  @Test
  fun `Frozen attribute`() = test("""
    from dataclasses import dataclass
    @dataclass(frozen=True)
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        attr: T  # read-only
    """.trimIndent())

  @Test
  fun `Frozen attribute callable parameter`() = test("""
    from typing import Callable
    from dataclasses import dataclass
    @dataclass(frozen=True)
    class A[T]:
    #       └ INFERRED_VARIANCE CONTRAVARIANT
        attr: Callable[[T], None]  # read-only
    """.trimIndent())

  @Test
  fun `Frozen attribute callable return`() = test("""
    from typing import Callable
    from dataclasses import dataclass
    @dataclass(frozen=True)
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        attr: Callable[[], T]  # read-only
    """.trimIndent())

  @Test
  @TestFor(issues=["PY-90269"])
  fun `Frozen attribute via dataclass_transform frozen_default`() = test("""
    from typing import dataclass_transform
    
    @dataclass_transform(frozen_default=True)
    def model(cls): ...
    
    @model
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        attr: T  # read-only
    """.trimIndent())

  @Test
  @TestFor(issues=["PY-90269"])
  fun `Mutable attribute via dataclass_transform frozen_default overridden`() = test("""
    from typing import dataclass_transform, Callable
    
    @dataclass_transform(frozen_default=True)
    def model(frozen: bool = True) -> Callable: ...
    
    @model(frozen=False)
    class A[T]:
    #       └ INFERRED_VARIANCE INVARIANT
        attr: T  # mutable
    """.trimIndent())

  @Test
  fun `Alias to contravariant class`() = test("""
    class A[T]:
        def f(self, t: T): pass
    type B[U] = A[U]
    #      └ INFERRED_VARIANCE CONTRAVARIANT
    """.trimIndent())

  @Test
  fun `Alias to covariant class`() = test("""
    class A[T]:
        def f(self) -> T: pass
    type B[U] = A[U]
    #      └ INFERRED_VARIANCE COVARIANT
    """.trimIndent())

  @Test
  fun `Alias to union of class`() = test("""
    class A[S]:
        def f(self, t: S): pass
    class B[T]:
        def f(self) -> T: pass
    type C[U] = A[U] | B[U]
    #      └ INFERRED_VARIANCE INVARIANT
    """.trimIndent())

  @Test
  fun `Alias to tuple`() = test("""
    type A[T] = tuple[T]
    #      └ INFERRED_VARIANCE COVARIANT
    """.trimIndent())

  @Test
  fun `Generic function with type var declared via custom function`() = test("""
    from typing import TypeVar
    T = TypeVar("T")
    
    def make_tv(): return T
    
    X = make_tv()
    
    # We cannot infer variance for type variables whose origin was disguised
    def fn(t: X): pass
    #         └ INFERRED_VARIANCE NULL
    #         └ WARNING Invalid type annotation
    """.trimIndent())

  @Test
  fun `Type in string literal`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        def method(self) -> "T": pass
    """.trimIndent())

  @Test
  fun `Type in string literal with Callable`() = test("""
    from typing import Callable
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        def method(self, arg: "Callable[[T], None]"): pass
    """.trimIndent())

  @Test
  fun `Recursive generic classes`() = test("""
    class A[T]:
    #       └ INFERRED_VARIANCE COVARIANT
        def method(self) -> B[T]: pass
    
    class B[U]:
        def method(self) -> A[U]: pass
    """.trimIndent())

  @Test
  fun `Parameter specification contravariant`() = test("""
    from typing import Callable
    
    class A[**P]:
    #         └ INFERRED_VARIANCE CONTRAVARIANT
        def f(self, *args: P.args, **kwargs: P.kwargs): ...
    """.trimIndent())

  @Test
  fun `Parameter specification flipped contravariant`() = test("""
    from typing import Callable
    
    class A[**P]:
    #         └ INFERRED_VARIANCE CONTRAVARIANT
        def f(self) -> Callable[P, None]: ...
    """.trimIndent())

  @Test
  fun `Parameter specification flipped covariant`() = test("""
    from typing import Callable
    
    class A[**P]:
    #         └ INFERRED_VARIANCE COVARIANT
        def f(self, f: Callable[P, None]): ...
    """.trimIndent())

  @Test
  fun `Parameter specification invariant`() = test("""
    from typing import Callable
    
    class A[**P]:
    #         └ INFERRED_VARIANCE INVARIANT
        def f(self, f: Callable[P, None]) -> Callable[P, None]: ...
    """.trimIndent())

  @Test
  fun `Type variable tuple covariant`() = test("""
    from typing import Callable
    
    class A[*Ts]:
    #        └ INFERRED_VARIANCE COVARIANT
        def f(self) -> tuple[*Ts]: ...
    """.trimIndent())

  @Test
  fun `Type variable tuple contravariant`() = test("""
    from typing import Callable
    
    class A[*Ts]:
    #        └ INFERRED_VARIANCE CONTRAVARIANT
        def f(self, t: tuple[*Ts]): ...
    """.trimIndent())

  @Test
  fun `Type variable tuple invariant`() = test("""
    from typing import Callable
    
    class A[*Ts]:
    #        └ INFERRED_VARIANCE INVARIANT
        def f(self, t: tuple[*Ts]) -> tuple[*Ts]: ...
    """.trimIndent())

}