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


@dataclasses.dataclass
class B1:
    a: int = dataclasses.field()
    b: int


@dataclasses.dataclass
class B2:
    <error descr="Fields with a default value must come after any fields without a default.">a</error>: int = dataclasses.field(default=1)
    b: int = dataclasses.field()


@dataclasses.dataclass
class B3:
    <error descr="Fields with a default value must come after any fields without a default.">a</error>: int = dataclasses.field(default_factory=int)
    b: int = dataclasses.field()


@dataclasses.dataclass
class C1:
    x: int = dataclasses.MISSING
    y: int


@dataclasses.dataclass
class C2:
    x: int = dataclasses.field(default=dataclasses.MISSING)
    y: int

C2(1, 2)


@dataclasses.dataclass
class C3:
    x: int = dataclasses.field(default_factory=dataclasses.MISSING)
    y: int

C3(1, 2)