def foo(x):
    print(x)
    del x

def bar():
    y = 1
    x = y
    print(x)
    del x
    print(y)
