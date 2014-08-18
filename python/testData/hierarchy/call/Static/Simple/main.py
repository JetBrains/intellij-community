def func1():
    pass

def func2():
    target_func()

def target_func():
    func1()

target_<caret>func()
