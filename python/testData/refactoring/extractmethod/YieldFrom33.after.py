def f(x, y):
    yield 'foo'
    return x, y


def g(in1, in2):
    out1, out2 = yield from bar(in1, in2)
    print(out1, out2)


def bar(in1_new, in2_new):
    return (yield from f(in1_new, in2_new))