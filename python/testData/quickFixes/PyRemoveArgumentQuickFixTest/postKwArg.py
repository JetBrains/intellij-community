def foo(a, **args):
    pass

b = {}
foo(**b, <error descr="Python versions < 3.5 do not allow keyword arguments after **expression">a<caret>=1</error>)


