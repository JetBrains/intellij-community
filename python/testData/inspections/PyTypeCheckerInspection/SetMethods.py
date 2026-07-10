
def foo(xs, ys):
    """
    :type xs: set[int]
    :type ys: set[string]
    """
    'foo' + <warning descr="Expected type 'AnyStr ≤: Union[str, unicode]', got 'int' instead">xs.pop()</warning>
    xs.discard(<warning descr="Expected type 'int', got 'str' instead">'foo'</warning>)
    xs.remove(<warning descr="Expected type 'int', got 'str' instead">'bar'</warning>)
    xs.add(<warning descr="Expected type 'int', got 'object' instead">object()</warning>)

    ys.extend(xs)
