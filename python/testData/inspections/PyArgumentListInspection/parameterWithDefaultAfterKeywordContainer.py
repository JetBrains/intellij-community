def foo(**kwargs): pass

two = 0
kw = {}
foo(**kw, <error descr="Python version 2.7 does not allow keyword arguments after **expression">two=1</error>)
