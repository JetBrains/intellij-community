"""
Tests the evaluation of calls to constructors when there is a __init__
method defined.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/constructors.html#init-method

from typing import Any, Generic, Self, TypeVar, assert_type, overload

T = TypeVar("T")


class Class1(Generic[T]):
    def __init__(self, x: T) -> None:
        pass


# Constructor calls for specialized classes
assert_type(Class1[int](1), Class1[int])
assert_type(Class1[float](1), Class1[float])
Class1[int](1.0)  # E

# Constructor calls for non-specialized classes
assert_type(Class1(1), Class1[int])
assert_type(Class1(1.0), Class1[float])


# > If the self parameter within the __init__ method is not annotated, type
# > checkers should infer a type of Self.


class Class2(Generic[T]):
    def __init__(self, x: Self | None) -> None:
        pass


class Class3(Class2[int]):
    pass


Class3(Class3(None))  # OK
Class3(Class2(None))  # E


# > Regardless of whether the self parameter type is explicit or inferred, a
# > type checker should bind the class being constructed to this parameter and
# > report any type errors that arise during binding.


class Class4(Generic[T]):
    def __init__(self: "Class4[int]") -> None: ...


Class4()  # OK
Class4[int]()  # OK
Class4[str]()  # E


class Class5(Generic[T]):
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
assert_type(Class5[int](3), Class5[int])
assert_type(Class5(""), Class5[set[str]])
assert_type(Class5(3.0), Class5[float])

# > Function-scoped type variables can also be used in the self annotation
# > of an __init__ method to influence the return type of the constructor call.

T1 = TypeVar("T1")
T2 = TypeVar("T2")

V1 = TypeVar("V1")
V2 = TypeVar("V2")


class Class6(Generic[T1, T2]):
    def __init__(self: "Class6[V1, V2]", value1: V1, value2: V2) -> None: ...


assert_type(Class6(0, ""), Class6[int, str])
assert_type(Class6[int, str](0, ""), Class6[int, str])


class Class7(Generic[T1, T2]):
    def __init__(self: "Class7[V2, V1]", value1: V1, value2: V2) -> None: ...


assert_type(Class7(0, ""), Class7[str, int])
assert_type(Class7[str, int](0, ""), Class7[str, int])


# > Class-scoped type variables should not be used in the self annotation.


class Class8(Generic[T1, T2]):
    def __init__(self: "Class8[T2, T1]") -> None:  # E
        pass


# > If a class does not define a __new__ method or __init__ method and does
# > not inherit either of these methods from a base class other than object,
# > a type checker should evaluate the argument list using the __new__ and
# > __init__ methods from the object class.


class Class9:
    pass


class Class10:
    pass


class Class11(Class9, Class10):
    pass


assert_type(Class11(), Class11)
Class11(1)  # E
