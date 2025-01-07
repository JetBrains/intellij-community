from typing import TypedDict, Optional, Union, Mapping, Any, Protocol


class A(TypedDict):
    x: Optional[int]
class B(TypedDict):
    x: Optional[int]
def f(a: A) -> None:
    a['x'] = None

b: B = B(x=0)
f(b)


class C(TypedDict):
    x: Union[int, str]
c: C = C(x = '0')
f(<warning descr="Expected type 'A', got 'C' instead">c</warning>)


class D(TypedDict):
    x: int
def bar(a: A) -> None:
    a['x'] = None
d: D = {'x': 0}
bar(<warning descr="Expected type 'A', got 'D' instead">d</warning>)


class E(TypedDict):
    x: int
def f(d: Mapping[str, object]) -> None:
    print(d)
def g(d: Mapping[str, Any]) -> None:
    print(d)
def h(d: Mapping[str, int]) -> None:
    print(d)
e: E = E(x=1)
f(e)
g(e)
h(<warning descr="Expected type 'Mapping[str, int]', got 'E' instead">e</warning>)


class A1(TypedDict, total=False):
    x: int
    y: int
class B1(TypedDict, total=False):
    x: int
class C1(TypedDict, total=False):
    x: int
    y: str
def f1(a: A1) -> None:
    a['y'] = 1
def g1(b: B1) -> None:
    f1(<warning descr="Expected type 'A1', got 'B1' instead">b</warning>)


class A2(TypedDict, total=False):
    x: int
class B2(TypedDict):
    x: int
def f2(a: A2) -> None:
    del a['x']
b: B2 = {'x': 0}
f2(<warning descr="Expected type 'A2', got 'B2' instead">b</warning>)


class A3(TypedDict):
    x: str
class B3(TypedDict):
    x: str
    y: str
a: A3 = B3(x = '', y = '')
b: B3 = <warning descr="Expected type 'B3', got 'A3' instead">A3(x = '')</warning>


class P(Protocol):
    pass

class A4(TypedDict):
    x: int
def f3(a: A4):
    v1: dict[str, int] = <warning descr="Expected type 'dict[str, int]', got 'A4' instead">a</warning>
    v2: P = a
def f4(d: dict[str, int]):
    v: A4 = <warning descr="Expected type 'A4', got 'dict[str, int]' instead">d</warning>
