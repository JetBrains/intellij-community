def foo(x):
    x += 1
    print(x)

def bar():
    y = 1
    foo<caret>(y)
    print(y)
