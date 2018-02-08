
def foo(xs, ys):
    """
    :type xs: set[int]
    :type ys: set[string]
    """
    'foo' + <warning descr="Expected type 'AnyStr', got 'int' instead">xs.pop()</warning>
    xs.discard(<weak_warning descr="Expected type 'int' (matched generic type '_T'), got 'str' instead">'foo'</weak_warning>)
    xs.remove(<weak_warning descr="Expected type 'int' (matched generic type '_T'), got 'str' instead">'bar'</weak_warning>)
    xs.add(<weak_warning descr="Expected type 'int' (matched generic type '_T'), got 'object' instead">object()</weak_warning>)

    ys.extend(xs)
