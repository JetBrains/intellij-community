def test():
    xs = map(lambda x: x + 1, [1, 2, 3])
    print('foo' + <warning descr="Expected type 'TypeVar('AnyStr', str, unicode)', got 'int' instead">xs[0]</warning>)
    ys = map(tuple, iter([1, 2, 3]))
    print(1 + <warning descr="Expected type 'int', got 'tuple' instead">ys[0]</warning>, 'bar' + <warning descr="Expected type 'TypeVar('AnyStr', str, unicode)', got 'tuple' instead">ys[1]</warning>)
