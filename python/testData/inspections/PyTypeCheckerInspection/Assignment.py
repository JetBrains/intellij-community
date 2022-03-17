def f():
    x1: int = <warning descr="Expected type 'int', got 'LiteralString' instead">'foo'</warning>
    x2: str = 'bar'
    x3: int = 0
    x4: str = <warning descr="Expected type 'str', got 'int' instead">1</warning>
