def foo(x):
    def bar():
        x = 10
    print(x)

def baz():
    foo<caret>(1)
