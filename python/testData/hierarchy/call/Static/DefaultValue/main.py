def target_func():
    pass


def func1():
    def inner_func1(x=target_func):
        pass

    return inner_func1(target_func)


def func2():
    def inner_func2(x=target_func()):
        pass

    return inner_func2(target_func)


def func3(x=target_func()):
    pass


def func4():
    def inner_func4(x=target_func):
        pass

    return inner_func4(target_func())


target_<caret>func()