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
        raise NotImplementedError

    def __set__(self, __obj: object, __value: int) -> None:
        ...


@dataclass
class DC1:
    y: Desc1 = Desc1()


dc1 = DC1(3)

assert_type(dc1.y, int)
assert_type(DC1.y, Desc1)
