from typing import TypedDict, List, Optional, Union


class Point(TypedDict):
    x: int
    y: int


def a(x: List[int]) -> Point:
    return <warning descr="Expected type 'Point', got 'list[list[int]]' instead">[x]</warning>

def b(x: int) -> Point:
    return <warning descr="TypedDict 'Point' has missing key: 'y'">{'x': 42}</warning>

def c() -> Point:
    return {'x': <warning descr="Expected type 'int', got 'str' instead">'abc'</warning>, 'y': 42}

def d() -> Point:
    return {'x': 42, 'y': 42, <warning descr="Extra key 'k' for TypedDict 'Point'">'k': 42</warning>}

def e1(x: int):
    return {'x': x}

def e(x: int) -> Point:
    return <warning descr="Expected type 'Point', got 'dict[str, int]' instead">e1(x)</warning>

def f1(x: int) -> Point:
    pass

def f(x: str) -> Point:
    return f1(int(x))

def g() -> Point:
    x = int(input())
    y = {'x': x}
    if x > 0:
        return <warning descr="Expected type 'Point', got 'dict[str, int]' instead">y</warning>
    elif x == 0:
        return Point(x=442, y=42)
    else:
        <warning descr="Expected type 'Point', got 'None' instead">return</warning>

def h(x) -> <warning descr="Expected to return 'Point', got no return">Point</warning>:
    x = 42

def i() -> <warning descr="Expected to return 'Point', got no return">Point</warning>:
    if True:
        pass