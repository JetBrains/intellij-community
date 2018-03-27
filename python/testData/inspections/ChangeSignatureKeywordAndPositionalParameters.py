def f(x, foo=1):
    pass


f<caret>(x, 42, <warning descr="Unexpected argument">bar='spam'</warning>)