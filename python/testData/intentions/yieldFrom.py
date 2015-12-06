def f(g):
    yield 'begin'
    for x in g():
        yield <caret>x
    print('end')
