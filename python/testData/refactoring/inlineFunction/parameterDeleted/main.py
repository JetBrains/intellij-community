def foo(x):
    print(x)
    del x

def bar():
    y = 1
    foo<caret>(y)
    print(y)
