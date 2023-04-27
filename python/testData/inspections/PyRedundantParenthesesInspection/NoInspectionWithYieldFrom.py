def f():
    yield from ('a', 'b', 'c', 'd')

def f():
    yield from (('a', 'b'), ('c', 'd'))
