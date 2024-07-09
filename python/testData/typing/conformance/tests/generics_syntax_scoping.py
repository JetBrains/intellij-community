"""
Tests the scoping rules for type parameter syntax introduced in PEP 695.
"""

# Specification: https://peps.python.org/pep-0695/#type-parameter-scopes

from typing import Callable, Mapping, Sequence, TypeVar, assert_type

# > A compiler error or runtime exception is generated if the definition
# > of an earlier type parameter references a later type parameter even
# > if the name is defined in an outer scope.


class ClassA[S, T: Sequence[S]]:  # E
    ...


class ClassB[S: Sequence[T], T]:  # E
    ...


class Foo[T]:
    ...


class BaseClassC[T]:
    def __init_subclass__(cls, param: type[Foo[T]]) -> None:
        ...


class ClassC[T](BaseClassC[T], param=Foo[T]):  # OK
    ...


print(T)  # E: Runtime error: 'T' is not defined


def decorator1[
    T, **P, R
](x: type[Foo[T]]) -> Callable[[Callable[P, R]], Callable[P, R]]:
    ...


@decorator1(Foo[T])  # E: Runtime error: 'T' is not defined
class ClassD[T]:
    ...


type Alias1[K, V] = Mapping[K, V] | Sequence[K]


S: int = int(0)


def outer1[S](x: str):
    S: str = x
    T: int = 1

    def outer2[T]():
        def inner1():
            nonlocal S  # OK
            assert_type(S, str)
            # nonlocal T  # Syntax error

        def inner2():
            global S  # OK
            assert_type(S, int)


class Outer1:
    class Private:
        pass

    class Inner[T](Private, Sequence[T]):  # OK
        pass

    def method1[T](self, a: Inner[T]) -> Inner[T]:  # OK
        return a


def decorator2[**P, R](x: int) -> Callable[[Callable[P, R]], Callable[P, R]]:
    ...


T = int(0)


@decorator2(T)  # OK
class ClassE[T](Sequence[T]):
    T = int(1)

    def method1[T](self):  # E
        ...

    def method2[T](self, x=T):  # E
        ...

    def method3[T](self, x: T):  # E
        ...


T = int(0)


class Outer2[T]:
    T = int(1)

    assert_type(T, int)

    class Inner1:
        T = str("")

        assert_type(T, str)

        def inner_method(self):
            assert_type(T, TypeVar)

    def outer_method(self):
        T = 3j

        assert_type(T, complex)

        def inner_func():
            assert_type(T, complex)
