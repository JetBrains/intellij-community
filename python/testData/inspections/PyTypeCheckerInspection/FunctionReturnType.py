from typing import Optional, List, Union

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
    # type: (int) -> List[str]
    return [str(x)]

def e():
    # type: () -> int
    pass

def f():
    # type: () -> Optional[str]
    x = int(input())
    if x > 0:
        return <warning descr="Expected type 'Optional[str]', got 'int' instead">42</warning>
    elif x == 0:
        return 'abc'
    else:
        return

def g(x):
    # type: (Any) -> int
    if x:
        return <warning descr="Expected type 'int', got 'str' instead">'abc'</warning>
    else:
        return <warning descr="Expected type 'int', got 'dict' instead">{}</warning>

def h(x):
    # type: (Any) -> int
    <warning descr="Expected type 'int', got 'None' instead">return</warning>

def i():
    # type: () -> Union[int, str]
    pass

def j(x):
    <warning descr="Expected to return 'Union[int, str]', got no return"># type: () -> Union[int, str]</warning>
    x = 42

def k():
    # type: () -> None
    if True:
        pass