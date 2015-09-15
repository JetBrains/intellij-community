from typing import Iterable, Iterator, Sequence, List, Mapping, Dict


def f1() -> Iterable[int]:
    pass


def f2() -> Iterator[int]:
    pass


def f3() -> Sequence[int]:
    pass


def f4() -> List[int]:
    pass


def f5() -> Mapping[str, int]:
    pass


def f6() -> Dict[str, int]:
    pass


for x in f1():
    pass

for x in f2():
    pass

for x in f3():
    pass

for x in f4():
    pass

for x in f5():
    pass

for x in f6():
    pass
