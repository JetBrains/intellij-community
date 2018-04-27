import dataclasses
from typing import ClassVar, Dict, List, Set, Tuple, Type
from collections import OrderedDict


@dataclasses.dataclass
class A:
    a: List[int] = <error descr="Mutable default '[]' is not allowed. Use 'default_factory'">[]</error>
    b: List[int] = <error descr="Mutable default 'list()' is not allowed. Use 'default_factory'">list()</error>
    c: Set[int] = <error descr="Mutable default '{1}' is not allowed. Use 'default_factory'">{1}</error>
    d: Set[int] = <error descr="Mutable default 'set()' is not allowed. Use 'default_factory'">set()</error>
    e: Tuple[int, ...] = ()
    f: Tuple[int, ...] = tuple()
    g: ClassVar[List[int]] = []
    h: ClassVar = []
    i: Dict[int, int] = <error descr="Mutable default '{1: 2}' is not allowed. Use 'default_factory'">{1: 2}</error>
    j: Dict[int, int] = <error descr="Mutable default 'dict()' is not allowed. Use 'default_factory'">dict()</error>
    k = []
    l = list()
    m: Dict[int, int] = <error descr="Mutable default 'OrderedDict()' is not allowed. Use 'default_factory'">OrderedDict()</error>
    n: FrozenSet[int] = frozenset()
    a2: Type[List[int]] = list
    b2: Type[Set[int]] = set
    c2: Type[Tuple[int, ...]] = tuple