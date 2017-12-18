import dataclasses
from typing import ClassVar


@dataclasses.dataclass
class A1:
    bar1: int
    <error descr="Fields with a default value must come after any fields without a default.">baz1</error>: int = 1
    foo1: int
    <error descr="Fields with a default value must come after any fields without a default.">bar2</error>: int = 2
    baz2: int
    foo2: int = 3


@dataclasses.dataclass()
class A2:
    bar: int
    baz: str = ""
    foo: int = 5


@dataclasses.dataclass
class A3:
    bar1: int
    baz1: ClassVar[int] = 1
    foo1: int
    bar2: ClassVar[int] = 2
    baz2: int
    foo2: int = 3


@dataclasses.dataclass
class A4:
    bar1: int
    baz1: ClassVar = 1
    foo1: int
    bar2: ClassVar = 2
    baz2: int
    foo2: int = 3