
x = 1

def func1():
    pass

def func2():
    global x
    if x > 0:
        func3()
    else:
        func1()

def func3():
    return func2

def func4(func_param):
    func_res = func_param()
    if func_res is not None:
        func_res()
    func1()