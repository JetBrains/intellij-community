def foo(x):
    def bar():
        x = 10
    print(x)

def baz():
    def bar():
        x = 10

    print(1)
