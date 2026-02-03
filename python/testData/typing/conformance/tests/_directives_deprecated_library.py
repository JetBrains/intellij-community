"""
Support module for directive_deprecated.
"""

from typing import Self, overload
from typing_extensions import deprecated


@deprecated("Use Spam instead")
class Ham:
    ...


@deprecated("It is pining for the fjords")
def norwegian_blue(x: int) -> int:
    ...


@overload
@deprecated("Only str will be allowed")
def foo(x: int) -> str:
    ...


@overload
def foo(x: str) -> str:
    ...


def foo(x: int | str) -> str:
    ...


class Spam:
    @deprecated("There is enough spam in the world")
    def __add__(self, other: object) -> Self:
        ...

    @property
    @deprecated("All spam will be equally greasy")
    def greasy(self) -> float:
        ...

    @property
    def shape(self) -> str:
        ...

    @shape.setter
    @deprecated("Shapes are becoming immutable")
    def shape(self, value: str) -> None:
        ...
