import dataclasses
from typing import ClassVar

from decorator import my_dataclass, my_field, MyField, registry

reg = registry()

@my_dataclass()
class A1:
    bar1: int
    <error descr="Fields with a default value must come after any fields without a default.">baz1</error>: int = 1
    foo1: int
    <error descr="Fields with a default value must come after any fields without a default.">bar2</error>: int = 2
    baz2: int
    foo2: int = 3


@my_dataclass()
class A2:
    bar: int
    baz: str = ""
    foo: int = 5


@my_dataclass()
class A3:
    bar1: int
    baz1: ClassVar[int] = 1
    foo1: int
    bar2: ClassVar[int] = 2
    baz2: int
    foo2: int = 3


@my_dataclass()
class A4:
    bar1: int
    baz1: ClassVar = 1
    foo1: int
    bar2: ClassVar = 2
    baz2: int
    foo2: int = 3


@my_dataclass()
class B1:
    a: int = my_field()
    b: int


@my_dataclass()
class B2:
    <error descr="Fields with a default value must come after any fields without a default.">a</error>: int = my_field(default=1)
    b: int = my_field()


@my_dataclass()
class B3:
    <error descr="Fields with a default value must come after any fields without a default.">a</error>: int = my_field(default_factory=int)
    b: int = my_field()


@my_dataclass()
class B4:
    <error descr="Fields with a default value must come after any fields without a default.">a</error>: int = MyField(default=1)
    b: int = MyField()


@my_dataclass()
class B5:
    <error descr="Fields with a default value must come after any fields without a default.">a</error>: int = MyField(default_factory=int)
    b: int = MyField()


@my_dataclass()
class C1:
    x: int = dataclasses.MISSING
    y: int


@my_dataclass()
class C2:
    x: int = my_field(default=dataclasses.MISSING)
    y: int = MyField(default=dataclasses.MISSING)
    z: int

C2(1, 2, 3)


@my_dataclass()
class C3:
    x: int = my_field(default_factory=dataclasses.MISSING)
    y: int = MyField(default_factory=dataclasses.MISSING)
    z: int

C3(1, 2, 3)


@my_dataclass()
class D1:
    x: int = 0
    y: int = my_field(init=False)
    z: int = MyField(init=False)


@my_dataclass()
class E1:
    foo = "bar"  # <- has no type annotation, so doesn't count.
    baz: str

# see https://docs.sqlalchemy.org/en/20/orm/dataclasses.html
@reg.mapped_as_dataclass
class B5:
    <error descr="Fields with a default value must come after any fields without a default.">a</error>: int = MyField(default_factory=int)
    b: int = MyField()
