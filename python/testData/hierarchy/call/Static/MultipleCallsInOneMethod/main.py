def foo():
    return abc() * abc()

def bar():
    return foo() + foo()

def baz():
    a = bar()
    b = bar()
    return bar() + a + b

def abc():
    return xyz() + xyz() * xyz()

def xyz():
    return 2

foo<caret>()