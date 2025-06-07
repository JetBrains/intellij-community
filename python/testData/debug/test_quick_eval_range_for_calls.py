from __future__ import print_function

def foo():
    return 1

def bar():
    return lambda: 2

def baz():
    return lambda: lambda: 3

a = foo() + bar()() + baz()()()

print("BREAK HERE")
