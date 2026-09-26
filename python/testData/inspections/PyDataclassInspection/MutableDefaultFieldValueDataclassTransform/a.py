from collections import OrderedDict
from typing import ClassVar

from decorator import my_dataclass, field


@my_dataclass()
class A:
    a: list[int] = []
    b: list[int] = list()
    c: set[int] = {1}
    d: set[int] = set()
    e: tuple[int, ...] = ()
    f: tuple[int, ...] = tuple()
    g: ClassVar[list[int]] = []
    h: ClassVar = []
    i: dict[int, int] = {1: 2}
    j: dict[int, int] = dict()
    k = []
    l = list()
    m: dict[int, int] = OrderedDict()
    n: frozenset[int] = frozenset()
    o: list = field(default_factory=list)
    a2: type[list[int]] = list
    b2: type[set[int]] = set
    c2: type[tuple[int, ...]] = tuple