def func(x, y):
    """
    >>> func(True, <warning descr="Unresolved reference 'True2'">True2</warning>)
    """
    return True, <error descr="Unresolved reference 'True2'">True2</error>
