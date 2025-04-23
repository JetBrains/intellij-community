"""
Tests the conversion of constructors into Callable types.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/constructors.html#converting-a-constructor-to-callable


from typing import (
    Any,
    Callable,
    Generic,
    NoReturn,
    ParamSpec,
    Self,
    TypeVar,
    assert_type,
    overload,
    reveal_type,
)

P = ParamSpec("P")
R = TypeVar("R")
T = TypeVar("T")


def accepts_callable(cb: Callable[P, R]) -> Callable[P, R]:
    return cb


class Class1:
    def __init__(self, x: int) -> None:
        pass


r1 = accepts_callable(Class1)
reveal_type(r1)  # `def (x: int) -> Class1`
assert_type(r1(1), Class1)
r1()  # E
r1(y=1)  # E


class Class2:
    """No __new__ or __init__"""

    pass


r2 = accepts_callable(Class2)
reveal_type(r2)  # `def () -> Class2`
assert_type(r2(), Class2)
r2(1)  # E


class Class3:
    """__new__ and __init__"""

    def __new__(cls, *args, **kwargs) -> Self: ...

    def __init__(self, x: int) -> None: ...


r3 = accepts_callable(Class3)
reveal_type(r3)  # `def (x: int) -> Class3`
assert_type(r3(3), Class3)
r3()  # E
r3(y=1)  # E
r3(1, 2)  # E


class Class4:
    """__new__ but no __init__"""

    def __new__(cls, x: int) -> int: ...


r4 = accepts_callable(Class4)
reveal_type(r4)  # `def (x: int) -> int`
assert_type(r4(1), int)
r4()  # E
r4(y=1)  # E


class Meta1(type):
    def __call__(cls, *args: Any, **kwargs: Any) -> NoReturn:
        raise NotImplementedError("Class not constructable")


class Class5(metaclass=Meta1):
    """Custom metaclass that overrides type.__call__"""

    def __new__(cls, *args: Any, **kwargs: Any) -> Self:
        """This __new__ is ignored for purposes of conversion"""
        return super().__new__(cls)


r5 = accepts_callable(Class5)
reveal_type(r5)  # `def (*args: Any, **kwargs: Any) -> NoReturn`

try:
    assert_type(r5(), NoReturn)
except:
    pass

try:
    assert_type(r5(1, x=1), NoReturn)
except:
    pass


class Class6Proxy: ...


class Class6:
    """__new__ that causes __init__ to be ignored"""

    def __new__(cls) -> Class6Proxy:
        return Class6Proxy()

    def __init__(self, x: int) -> None:
        """This __init__ is ignored for purposes of conversion"""
        pass


r6 = accepts_callable(Class6)
reveal_type(r6)  # `def () -> Class6Proxy`
assert_type(r6(), Class6Proxy)
r6(1)  # E


class Class6Any:
    """__new__ that causes __init__ to be ignored via Any"""

    def __new__(cls) -> Any:
        return super().__new__(cls)

    def __init__(self, x: int) -> None:
        """This __init__ is ignored for purposes of conversion"""
        pass


r6_any = accepts_callable(Class6Any)
reveal_type(r6_any)  # `def () -> Any`
assert_type(r6_any(), Any)
r6_any(1)  # E

# > If the __init__ or __new__ method is overloaded, the callable type should
# > be synthesized from the overloads. The resulting callable type itself will
# > be overloaded.


class Class7(Generic[T]):
    @overload
    def __init__(self: "Class7[int]", x: int) -> None: ...
    @overload
    def __init__(self: "Class7[str]", x: str) -> None: ...
    def __init__(self, x: int | str) -> None:
        pass


r7 = accepts_callable(Class7)
reveal_type(
    r7
)  # overload of `def (x: int) -> Class7[int]` and `def (x: str) -> Class7[str]`
assert_type(r7(0), Class7[int])
assert_type(r7(""), Class7[str])


# > If the class is generic, the synthesized callable should include any
# > class-scoped type parameters that appear within the signature, but these
# > type parameters should be converted to function-scoped type parameters
# > for the callable. Any function-scoped type parameters in the __init__
# > or __new__ method should also be included as function-scoped type parameters
# > in the synthesized callable.


class Class8(Generic[T]):
    def __new__(cls, x: list[T], y: list[T]) -> Self:
        return super().__new__(cls)


r8 = accepts_callable(Class8)
reveal_type(r8)  # `def [T] (x: T, y: list[T]) -> Class8[T]`
assert_type(r8([""], [""]), Class8[str])
r8([1], [""])  # E


class Class9:
    def __init__(self, x: list[T], y: list[T]) -> None:
        pass


r9 = accepts_callable(Class9)
reveal_type(r9)  # `def [T] (x: list[T], y: list[T]) -> Class9`
assert_type(r9([""], [""]), Class9)
r9([1], [""])  # E
