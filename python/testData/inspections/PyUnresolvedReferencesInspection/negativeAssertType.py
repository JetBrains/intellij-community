def f(c):
    x = 'foo' if c else ['f', 'o', 'o']
    if not isinstance(x, str):
        raise TypeError('foo')
    return x.<warning descr="Unresolved attribute reference 'pop' for class 'str'">pop</warning>()  # should warn about using 'pop()' on a 'str' instance
