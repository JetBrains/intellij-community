def foo(**kwargs): pass

two = 0
kw = {}
foo(**kw, <error descr="Cannot appear past keyword arguments or *arg or **kwarg">two=1</error>)
