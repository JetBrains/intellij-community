def f1():
    x: int = 0
    x = <warning descr="Expected type 'int', got 'str' instead">'foo'</warning>
    x = 1
    x = <warning descr="Expected type 'int', got 'str' instead">'bar'</warning>
    y: str = 'foo'
    y = 'bar'
    y = <warning descr="Expected type 'str', got 'int' instead">0</warning>
    z: int
    z: str
    z = <warning descr="Expected type 'str', got 'int' instead">1</warning>
    z = "aba"


def f2(p: int):
    p = <warning descr="Expected type 'int', got 'str' instead">"aba"</warning>


def f3(p: int):
    p: str = "aba"


def f4(p: int):
    p: str
    p = "aba"


v_global: int


def outer():
    global v_global
    v_global = <warning descr="Expected type 'int', got 'str' instead">"abb"</warning>

    v: int

    def inner():
        nonlocal v
        v = <warning descr="Expected type 'int', got 'str' instead">"abb"</warning>