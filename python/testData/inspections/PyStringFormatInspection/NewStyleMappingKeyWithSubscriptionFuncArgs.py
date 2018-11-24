def f():
    return [1, 2, 3]

"{foo[1]}".format(foo=f())
<warning descr="Too few arguments for format string">"{foo[3]}"</warning>.format(foo=f())

def g():
    return 1, 2, 3

"{foo[1]:d}".format(foo=g())
<warning descr="Too few arguments for format string">"{foo[3]}"</warning>.format(foo=g())

def ff():
    return g()

"{foo[1]}".format(foo=g())
<warning descr="Too few arguments for format string">"{foo[3]}"</warning>.format(foo=ff())
