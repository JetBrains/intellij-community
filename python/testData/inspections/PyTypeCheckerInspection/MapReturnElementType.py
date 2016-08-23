def test():
    xs = map(lambda x: x + 1, [1, 2, 3])
    print('foo' + xs[0])  # Can be a str since map returns list[V] | str | unicode
    ys = map(tuple, iter([1, 2, 3]))
    print(1 + <warning descr="Expected type 'Number', got 'Union[tuple, str, unicode]' instead">ys[0]</warning>, 'bar' + ys[1])
