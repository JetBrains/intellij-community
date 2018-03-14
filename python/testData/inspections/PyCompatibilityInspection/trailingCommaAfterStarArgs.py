def foo(*args, **kwargs):
    print(args, kwargs)


foo(1, 2, 3, *[3]<error descr="Python versions < 3.5 do not allow a trailing comma after *expression"><warning descr="Python versions < 3.5 do not allow a trailing comma after *expression">,</warning></error>)
foo(1, 2, 3, *[3], **{'d': 'd'}<error descr="Python versions < 3.5 do not allow a trailing comma after **expression"><warning descr="Python versions < 3.5 do not allow a trailing comma after **expression">,</warning></error>)
