class Foo(object):
    bar = True

class FooMaker(object):
    __call__ = tuple

fm = FooMaker()
f3 = fm()

f3.c<caret>