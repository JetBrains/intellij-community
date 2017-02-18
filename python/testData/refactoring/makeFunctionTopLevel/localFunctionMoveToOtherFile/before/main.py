from sys import path

CONST = 42


class MyClass:
    pass


def f():
    def <caret>g():
        f()
        print(MyClass())
        print(path)
        print(CONST)
