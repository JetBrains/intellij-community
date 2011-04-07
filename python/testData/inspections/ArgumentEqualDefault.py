def foo(a, b = 345, c = 1):
    pass

#PY-3261
foo(1, <warning descr="Argument equals to default parameter value">345<caret></warning>, c=22)