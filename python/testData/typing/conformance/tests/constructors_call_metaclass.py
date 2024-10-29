"""
Tests the evaluation of calls to constructors when there is a custom
metaclass with a __call__ method.
"""

from typing import NoReturn, Self, TypeVar, assert_type

# Specification: https://typing.readthedocs.io/en/latest/spec/constructors.html#constructor-calls

# Metaclass __call__ method: https://typing.readthedocs.io/en/latest/spec/constructors.html#metaclass-call-method


class Meta1(type):
    def __call__(cls, *args, **kwargs) -> NoReturn:
        raise TypeError("Cannot instantiate class")


class Class1(metaclass=Meta1):
    def __new__(cls, x: int) -> Self:
        return super().__new__(cls)


assert_type(Class1(), NoReturn)


class Meta2(type):
    def __call__(cls, *args, **kwargs) -> "int | Meta2":
        return 1


class Class2(metaclass=Meta2):
    def __new__(cls, x: int) -> Self:
        return super().__new__(cls)


assert_type(Class2(), int | Meta2)

T = TypeVar("T")


class Meta3(type):
    def __call__(cls: type[T], *args, **kwargs) -> T:
        return super().__call__(cls, *args, **kwargs)


class Class3(metaclass=Meta3):
    def __new__(cls, x: int) -> Self:
        return super().__new__(cls)


Class3()  # E: Missing argument for 'x' parameter in __new__
assert_type(Class3(1), Class3)


class Meta4(type):
    def __call__(cls, *args, **kwargs):
        return super().__call__(cls, *args, **kwargs)


class Class4(metaclass=Meta4):
    def __new__(cls, x: int) -> Self:
        return super().__new__(cls)


Class4()  # E: Missing argument for 'x' parameter in __new__
assert_type(Class4(1), Class4)
