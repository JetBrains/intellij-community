# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#arbitrary-generic-types-as-base-classes

from typing import Generic, TypeVar, Iterable, assert_type

T = TypeVar("T")

# > Generic[T] is only valid as a base class – it’s not a proper type. However,
# > user-defined generic types [...] and built-in generic types and ABCs such as
# > list[T] and Iterable[T] are valid both as types and as base classes.


class Node: ...


class SymbolTable(dict[str, list[Node]]): ...


def takes_dict(x: dict): ...
def takes_dict_typed(x: dict[str, list[Node]]): ...
def takes_dict_incorrect(x: dict[str, list[object]]): ...


def test_symbol_table(s: SymbolTable):
    takes_dict(s)  # OK
    takes_dict_typed(s)  # OK
    takes_dict_incorrect(s)  # E


def func1(y: Generic[T]):  # E
    x: Generic  # E


# > If a generic base class has a type variable as a type argument, this makes
# > the defined class generic.

# Note that there is overlap in the spec and tests in generics_basic.py

from collections.abc import Iterable, Container, Iterator


class LinkedList(Iterable[T], Container[T]): ...


def test_linked_list(l: LinkedList[int]):
    assert_type(iter(l), Iterator[int])
    assert_type(l.__contains__(1), bool)


linked_list_invalid: LinkedList[int, int]  # E

from collections.abc import Mapping


class MyDict(Mapping[str, T]): ...


def test_my_dict(d: MyDict[int]):
    assert_type(d["a"], int)


my_dict_invalid: MyDict[int, int]  # E

# > Note that we can use T multiple times in the base class list,
# > as long as we don’t use the same type variable T multiple times
# > within Generic[...].


class BadClass1(Generic[T, T]):  # E
    pass


class GoodClass1(dict[T, T]):  # OK
    pass
