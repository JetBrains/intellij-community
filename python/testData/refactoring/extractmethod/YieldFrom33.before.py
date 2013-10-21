def f(x, y):
    yield 'foo'
    return x, y


def g(in1, in2):
    out1, out2 = <selection>yield from f(in1, in2)</selection>
    print(out1, out2)