def foo():
    pass


def return_foo():
    return foo


def target_func():
    return_foo()()

tf = target_func


def return_func():
    return tf


def func1():
    return_func()()


def func2():
    return_func()()

func1()
func2()

x = 1