def f():
    return dict(foo=0)

'{foo}'.format(**f())

<warning descr="Too few mapping keys">"{}"</warning>.format()