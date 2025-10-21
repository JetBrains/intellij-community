from typing import List, Optional, Union, Generator, Iterable

def a(x: List[int]) -> List[str]:
    return <warning descr="Expected type 'list[str]', got 'list[list[int]]' instead">[x]</warning>

def b(x: int) -> List[str]:
    return <warning descr="Expected type 'list[str]', got 'list[int]' instead">[1,2]</warning>

def c() -> int:
    return <warning descr="Expected type 'int', got 'str' instead">'abc'</warning>

def d(x: int) -> List[str]:
    return [str(x)]

def e() -> int:
    pass

def f() -> Optional[str]:
    x = int(input())
    if x > 0:
        return <warning descr="Expected type 'str | None', got 'int' instead">42</warning>
    elif x == 0:
        return 'abc'
    else:
        return

def g(x) -> int:
    if x:
        return <warning descr="Expected type 'int', got 'str' instead">'abc'</warning>
    else:
        return <warning descr="Expected type 'int', got 'dict[Any, Any]' instead">{}</warning>

def h(x) -> int:
    <warning descr="Expected type 'int', got 'None' instead">return</warning>

def i() -> Union[int, str]:
    pass

def j(x) -> <warning descr="Expected type 'int | str', got 'None' instead">Union[int, str]</warning>:
    x = 42

def k() -> None:
    if True:
        pass

def l(x) -> <warning descr="Expected type 'int', got 'int | None' instead">int</warning>:
    if x == 1:
        return 42
    
def m(x) -> None:
    """Does not display warning about implicit return, because annotated '-> None' """
    if x:
        return

def n() -> Generator[int, Any, str]:
    yield 13
    return <warning descr="Expected type 'str', got 'int' instead">42</warning>

def o(val) -> int:
    assert val is int
    return val

def t() -> Iterable[int]:
    yield 13
    return "str" # no warning here