def func1():
    pass

def func2():
    target_func()

def func3(f):
    func2()

def target_func():
    func1()

func3(target_<caret>func)
