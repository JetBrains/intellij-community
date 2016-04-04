from typing import List, Set


def f(x, y):
    # type: (int, List[int]) -> str
    y.append(x)
    return 'foo'


xs = {1, 2, 3} # type: Set[int]
