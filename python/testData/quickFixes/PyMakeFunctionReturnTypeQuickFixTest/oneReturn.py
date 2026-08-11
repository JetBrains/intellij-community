def f(x):
    # type: (int) -> str
    return <warning descr="Expected type 'str', got 'Literal[42]' instead">42<caret></warning>
