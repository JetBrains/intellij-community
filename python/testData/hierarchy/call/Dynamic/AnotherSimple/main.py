def func1():
    pass


def func2():
    pass


def func3():
    pass


def Func1(f):
    f()
    return Func1


def Func2(f):
    f()
    return f


def Func3(f):
    f()
    return f


def Func4(f):
    f()
    return f


def target_func(a=func1, b=func2, c=func3):
    a(), b(), c()


Func1(Func2(target_func))
Func2(Func3(Func4(target_func)))
Func3(target_func)
Func4(target_func)
target_func(func1, func2, func3)