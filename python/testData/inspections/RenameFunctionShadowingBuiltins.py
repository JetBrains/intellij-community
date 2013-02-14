def <warning descr="Shadows a built-in with the same name">i<caret>d</warning>(x):
    return x


def f():
    return id('foo')
