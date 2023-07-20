def f():
    x: int
    x = <warning descr="Expected type 'int', got 'LiteralString' instead">'foo'</warning>
    y: str
    y = 'bar'
