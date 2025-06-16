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
    def method4(self, x: int) -> int:
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
