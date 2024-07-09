"""
Tests the handling of descriptors within a dataclass.
"""

# This portion of the dataclass spec is under-specified in the documentation,
# but its behavior can be determined from the runtime implementation.

from dataclasses import dataclass
from typing import Any, Generic, TypeVar, assert_type, overload

T = TypeVar("T")


class Desc1:
    @overload
    def __get__(self, __obj: None, __owner: Any) -> "Desc1":
        ...

    @overload
    def __get__(self, __obj: object, __owner: Any) -> int:
        ...

    def __get__(self, __obj: object | None, __owner: Any) -> "int | Desc1":
        ...

    def __set__(self, __obj: object, __value: int) -> None:
        ...


@dataclass
class DC1:
    y: Desc1 = Desc1()


dc1 = DC1(3)

assert_type(dc1.y, int)
assert_type(DC1.y, Desc1)


class Desc2(Generic[T]):
    @overload
    def __get__(self, instance: None, owner: Any) -> list[T]:
        ...

    @overload
    def __get__(self, instance: object, owner: Any) -> T:
        ...

    def __get__(self, instance: object | None, owner: Any) -> list[T] | T:
        ...


@dataclass
class DC2:
    x: Desc2[int]
    y: Desc2[str]
    z: Desc2[str] = Desc2()


assert_type(DC2.x, list[int])
assert_type(DC2.y, list[str])
assert_type(DC2.z, list[str])

dc2 = DC2(Desc2(), Desc2(), Desc2())
assert_type(dc2.x, int)
assert_type(dc2.y, str)
assert_type(dc2.z, str)
