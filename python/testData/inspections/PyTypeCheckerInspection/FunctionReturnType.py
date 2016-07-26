from typing import List

def a(x):
    # type: (List[int]) -> List[str]
    return <warning descr="Expected type 'List[str]', got 'List[List[int]]' instead">[x]</warning>

def b(x):
    # type: (int) -> List[str]
    return <warning descr="Expected type 'List[str]', got 'List[int]' instead">[1,2]</warning>

def c():
    # type: () -> int
    return <warning descr="Expected type 'int', got 'str' instead">'abc'</warning>

def d(x):
    # type: (x: int) -> List[str]
    return [str(x)]