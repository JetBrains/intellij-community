def f():
    x: int = 0
    x = <warning descr="Expected type 'int', got 'LiteralString' instead">'foo'</warning>
    x = 1
    x = <warning descr="Expected type 'int', got 'LiteralString' instead">'bar'</warning>
    y: str = 'foo'
    y = 'bar'
    y = <warning descr="Expected type 'str', got 'int' instead">0</warning>
