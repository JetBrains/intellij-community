def foo(a, **args):
    pass

b = {}
foo(**b, <error descr="Python version 2.7 does not allow keyword arguments after **expression">a<caret>=1</error>)


