def <weak_warning descr="Shadows built-in name 'id'">i<caret>d</weak_warning>(x):
    return x


def f():
    return id('foo')
