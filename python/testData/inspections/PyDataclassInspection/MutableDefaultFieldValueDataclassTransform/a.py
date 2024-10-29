from collections import OrderedDict
from typing import ClassVar

from decorator import my_dataclass, field


@my_dataclass()
class A:
    a: list[int] = <error descr="Mutable default '[]' is not allowed. Use 'default_factory'">[]</error>
    b: list[int] = <error descr="Mutable default 'list()' is not allowed. Use 'default_factory'">list()</error>
    c: set[int] = <error descr="Mutable default '{1}' is not allowed. Use 'default_factory'">{1}</error>
    d: set[int] = <error descr="Mutable default 'set()' is not allowed. Use 'default_factory'">set()</error>
    e: tuple[int, ...] = ()
    f: tuple[int, ...] = tuple()
    g: ClassVar[list[int]] = []
    h: ClassVar = []
    i: dict[int, int] = <error descr="Mutable default '{1: 2}' is not allowed. Use 'default_factory'">{1: 2}</error>
    j: dict[int, int] = <error descr="Mutable default 'dict()' is not allowed. Use 'default_factory'">dict()</error>
    k = []
    l = list()
    m: dict[int, int] = <error descr="Mutable default 'OrderedDict()' is not allowed. Use 'default_factory'">OrderedDict()</error>
    n: frozenset[int] = frozenset()
    o: list = field(default_factory=list)
    a2: type[list[int]] = list
    b2: type[set[int]] = set
    c2: type[tuple[int, ...]] = tuple