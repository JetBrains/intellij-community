from typing import List, Optional, Union

def a(x: List[int]) -> List[str]:
    return <warning descr="Expected type 'List[str]', got 'List[List[int]]' instead">[x]</warning>

def b(x: int) -> List[str]:
    return <warning descr="Expected type 'List[str]', got 'List[int]' instead">[1,2]</warning>

def c() -> int:
    return <warning descr="Expected type 'int', got 'str' instead">'abc'</warning>

def d(x: int) -> List[str]:
    return [str(x)]

def e() -> int:
    pass

def f() -> Optional[str]:
    x = int(input())
    if x > 0:
        return <warning descr="Expected type 'Optional[str]', got 'int' instead">42</warning>
    elif x == 0:
        return 'abc'
    else:
        return

def g(x) -> int:
    if x:
        return <warning descr="Expected type 'int', got 'str' instead">'abc'</warning>
    else:
        return <warning descr="Expected type 'int', got 'dict' instead">{}</warning>

def h(x) -> int:
    <warning descr="Expected type 'int', got 'None' instead">return</warning>

def i() -> Union[int, str]:
    pass

def j(x) -> <warning descr="Expected to return 'Union[int, str]', got no return">Union[int, str]</warning>:
    x = 42

def k() -> None:
    if True:
        pass