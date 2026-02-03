class MyClass:
    foo = 'spam'


def f(foo):
    foo.illegal = 42
    MyClass().foo.<caret>
