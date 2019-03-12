def f():
    x: int
    x = <warning descr="Expected type 'int', got 'str' instead">'foo'</warning>
    y: str
    y = 'bar'
