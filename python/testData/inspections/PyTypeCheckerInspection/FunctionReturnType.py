def a():
    # type: () -> int
    return <warning descr="Expected type 'int', got 'str' instead">'abc'</warning>

def b(x):
    # type: (int) -> str
    return str(x)