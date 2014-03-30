def f(g):
    yield 'begin'
    yield from g()
    print('end')
