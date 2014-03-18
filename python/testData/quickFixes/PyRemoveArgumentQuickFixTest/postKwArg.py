def foo(a, **args):
    pass

b = {}
foo(**b, <error descr="Cannot appear past keyword arguments or *arg or **kwarg">a<caret>=1</error>)


