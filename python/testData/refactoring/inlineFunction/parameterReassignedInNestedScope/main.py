def foo(x):
    class C:
        x = 10
    print(x)

def baz():
    foo<caret>(1)
