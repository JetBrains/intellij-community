def fill(f):
    return lambda: f('test')


@fill
def test(x):
    return x


test()
