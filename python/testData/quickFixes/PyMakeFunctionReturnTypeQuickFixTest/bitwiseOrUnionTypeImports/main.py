import my


def foo(a) -> my.X:
    if a:
        return <warning descr="Expected type 'X', got 'type[X]' instead">my.X<caret></warning>
    else:
        return <warning descr="Expected type 'X', got 'type[Y]' instead">my.Y</warning>