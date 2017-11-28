def f():
    return dict(foo=0)

'{foo}'.format(**f())

<warning descr="Too few arguments for format string">"{}"</warning>.format()