from typing import List, <warning descr="Unused import statement">Optional</warning>


def f(x, y):
    # type: (int, List[int]) -> str
    y.append(x)
    return 'foo'