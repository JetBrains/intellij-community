def f(x, foo=1):
    pass


f<warning descr="Unexpected argument(s)"><caret>(x, 42, <warning descr="Unexpected argument">bar='spam'</warning>)</warning>