"""
Tests the typing.override decorator.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/class-compat.html#override

from typing import Any, Callable, overload, override


def wrapper(func: Callable[..., Any], /) -> Any:
    def wrapped(*args: Any, **kwargs: Any) -> Any:
        raise NotImplementedError

    return wrapped


class ParentA:
    def method1(self) -> int:
        return 1

    @overload
    def method2(self, x: int) -> int:
        ...

    @overload
    def method2(self, x: str) -> str:
        ...

    def method2(self, x: int | str) -> int | str:
        return 0

    def method5(self):
        pass


class ChildA(ParentA):
    @override
    def method1(self) -> int:  # OK
        return 2

    @overload
    def method2(self, x: int) -> int:
        ...

    @overload
    def method2(self, x: str) -> str:
        ...

    def method2(self, x: int | str) -> int | str:  # OK
        return 0

    @override  # E[method3]
    def method3(self) -> int:  # E[method3]: no matching signature in ancestor
        return 1

    @overload  # E[method4]
    def method4(self, x: int) -> int:  # E[method4]
        ...

    @overload
    def method4(self, x: str) -> str:
        ...

    @override  # E[method4]
    def method4(self, x: int | str) -> int | str:  # E[method4]: no matching signature in ancestor
        return 0

    @override
    @wrapper
    def method5(self):  # OK
        pass

    # > The @override decorator should be permitted anywhere a type checker
    # > considers a method to be a valid override, which typically includes not
    # > only normal methods but also @property, @staticmethod, and @classmethod.

    @staticmethod
    @override  # E[static_method1]
    def static_method1() -> int:  # E[static_method1]: no matching signature in ancestor
        return 1

    @classmethod
    @override  # E[class_method1]
    def class_method1(cls) -> int:  # E[class_method1]: no matching signature in ancestor
        return 1

    @property
    @override  # E[property1]
    def property1(self) -> int:  # E[property1]: no matching signature in ancestor
        return 1


# Test the case where the parent derives from Any

class ParentB(Any):
    pass


class ChildB(ParentB):
    @override
    def method1(self) -> None:  # OK
        pass


# > When type checkers encounter a method decorated with @typing.override they
# > should treat it as a type error unless that method is overriding a method or
# > attribute in some ancestor class, and the type of the overriding method is
# > assignable to the type of the overridden method.

# ``__init__`` and ``__new__`` are normally exempt from override compatibility
# checks, since constructors are not subject to the Liskov substitution
# principle. However, when they are explicitly decorated with ``@override`` the
# decorator's assignability check should still be honored.
# See https://github.com/python/typing/issues/2222


class ParentC:
    def __init__(self, x: int) -> None: ...

    def __new__(cls, x: int) -> "ParentC":
        raise NotImplementedError


class ChildC1(ParentC):
    @override
    def __init__(self, x: int) -> None: ...  # OK

    @override
    def __new__(cls, x: int) -> "ChildC1":  # OK
        raise NotImplementedError


class ChildC2(ParentC):
    @override  # E[init]
    def __init__(self, x: str) -> None: ...  # E[init]: not assignable to "ParentC.__init__"

    @override  # E[new]
    def __new__(cls, x: str) -> "ChildC2":  # E[new]: not assignable to "ParentC.__new__"
        raise NotImplementedError


# Without ``@override`` an incompatible constructor signature is allowed, since
# ``__init__`` and ``__new__`` are exempt from the usual override checks.


class ChildC3(ParentC):
    def __init__(self, x: str) -> None: ...  # OK

    def __new__(cls, x: str) -> "ChildC3":  # OK
        raise NotImplementedError
