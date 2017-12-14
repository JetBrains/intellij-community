import dataclasses
from typing import List, Set, Tuple


@dataclasses.dataclass
class A:
    a: List[int] = <error descr="mutable default 'list' is not allowed">[]</error>
    b: List[int] = <error descr="mutable default 'list' is not allowed">list()</error>
    c: Set[int] = <error descr="mutable default 'set' is not allowed">{1}</error>
    d: Set[int] = <error descr="mutable default 'set' is not allowed">set()</error>
    e: Tuple[int, ...] = <error descr="mutable default 'tuple' is not allowed">()</error>
    f: Tuple[int, ...] = <error descr="mutable default 'tuple' is not allowed">tuple()</error>