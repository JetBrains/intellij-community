from typing import NamedTuple


class FAILED(NamedTuple):
    bar1: int
    <error descr="Fields with a default value must come after any fields without a default.">baz1</error>: int = 1
    foo1: int
    <error descr="Fields with a default value must come after any fields without a default.">bar2</error>: int = 2
    baz2: int
    foo2: int = 3


class OK(NamedTuple):
    bar: int
    baz: str = ""
    foo: int = 5