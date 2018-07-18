import attr
from typing import ClassVar


@attr.s(auto_attribs=True)
class A1:
    bar1: int
    <error descr="Fields with a default value must come after any fields without a default.">baz1</error>: int = 1
    foo1: int
    <error descr="Fields with a default value must come after any fields without a default.">bar2</error>: int = 2
    baz2: int
    foo2: int = 3


@attr.s(auto_attribs=True)
class A2:
    bar1: int
    baz1: ClassVar[int] = 1
    foo1: int
    bar2: ClassVar[int] = 2
    baz2: int
    foo2: int = 3


@attr.s(auto_attribs=True)
class B1:
    a: int = attr.ib()
    b: int


@attr.s(auto_attribs=True)
class B2:
    <error descr="Fields with a default value must come after any fields without a default.">a</error>: int = attr.ib(default=1)
    b: int = attr.ib()


@attr.s(auto_attribs=True)
class B3:
    <error descr="Fields with a default value must come after any fields without a default.">a</error>: int = attr.ib(default=attr.Factory(int))
    b: int = attr.ib()


@attr.s
class C1:
    <error descr="Fields with a default value must come after any fields without a default.">x</error> = attr.ib()
    y = attr.ib()

    @x.default
    def name_does_not_matter(self):
        return 1


@attr.dataclass
class D1:
    x: int = attr.NOTHING
    y: int


@attr.dataclass
class D2:
    x: int = attr.ib(default=attr.NOTHING)
    y: int