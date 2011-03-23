def foo(a, b = 345, c = 1):
    pass

foo(1, <warning descr="Argument equals to default parameter value">345<caret></warning>, 22)