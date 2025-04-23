"""
Tests the class syntax for defining a TypedDict.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/typeddict.html#typeddict

from typing import Generic, TypeVar, TypedDict


class Movie(TypedDict):
    name: str
    year: int

    # > String literal forward references are valid in the value types.
    director: "Person"


class Person(TypedDict):
    name: str
    age: int


# > Methods are not allowed, since the runtime type of a TypedDict object
# > will always be just dict (it is never a subclass of dict).
class BadTypedDict1(TypedDict):
    name: str

    # Methods are not allowed, so this should generate an error.
    def method1(self):  # E
        pass

    # Methods are not allowed, so this should generate an error.
    @classmethod  # E[method2]
    def method2(cls):  # E[method2]
        pass

    # Methods are not allowed, so this should generate an error.
    @staticmethod  # E[method3]
    def method3():  # E[method3]
        pass


# > Specifying a metaclass is not allowed.
class BadTypedDict2(TypedDict, metaclass=type):  # E
    name: str


# This should generate an error because "other" is not an allowed keyword argument.
class BadTypedDict3(TypedDict, other=True):  # E
    name: str


# > TypedDicts may be made generic by adding Generic[T] among the bases.
T = TypeVar("T")


class GenericTypedDict(TypedDict, Generic[T]):
    name: str
    value: T


# > An empty TypedDict can be created by only including pass in the
# > body (if there is a docstring, pass can be omitted):
class EmptyDict1(TypedDict):
    pass


class EmptyDict2(TypedDict):
    """Docstring"""


class MovieTotal(TypedDict, total=True):
    name: str


class MovieOptional(TypedDict, total=False):
    name: str
