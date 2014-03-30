def test():
    xs = map(lambda x: x + 1, [1, 2, 3])
    print('foo' + <warning descr="Expected type 'str | unicode', got 'int' instead">xs[0]</warning>)
    ys = map(str, iter([1, 2, 3]))
    print(1 + <warning descr="Expected type 'Number', got 'str' instead">ys[0]</warning>, 'bar' + ys[1])
