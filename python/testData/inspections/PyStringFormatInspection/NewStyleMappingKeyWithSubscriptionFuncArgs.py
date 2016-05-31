def f():
    return [1, 2, 3]

"{foo[1]}".format(foo=f())
"{foo[3]}".format(foo=<warning descr="Too few arguments for format string">f()</warning>)

def g():
    return 1, 2, 3

"{foo[1]:d}".format(foo=g())
"{foo[3]}".format(foo=<warning descr="Too few arguments for format string">g()</warning>)

def ff():
    return g()

"{foo[1]}".format(foo=g())
"{foo[3]}".format(foo=<warning descr="Too few arguments for format string">ff()</warning>)
