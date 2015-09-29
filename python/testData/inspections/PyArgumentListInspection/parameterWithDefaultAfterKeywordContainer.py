def foo(**kwargs): pass

two = 0
kw = {}
foo(**kw, <error descr="Python versions < 3.5 do not allow keyword arguments after **expression">two=1</error>)
