import dataclasses


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