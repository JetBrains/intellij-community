def foo(*args, **kwargs):
    print(args, kwargs)


foo(1, 2, 3, *[3]<error descr="Python version 3.4 does not allow a trailing comma after *expression"><warning descr="Python version 2.6, 2.7 do not allow a trailing comma after *expression">,</warning></error>)
foo(1, 2, 3, *[3], **{'d': 'd'}<error descr="Python version 3.4 does not allow a trailing comma after **expression"><warning descr="Python version 2.6, 2.7 do not allow a trailing comma after **expression">,</warning></error>)
