def foo(a, b = 345, c = 1):
    pass

foo(1, <warning descr="Argument equals to default parameter value">345</warning>, 22)

a = dict()
a.get(1, <warning descr="Argument equals to default parameter value">None</warning>)