class Foo(object):
    bar = True

class FooMaker(object):
    def foo(self):
        return Foo()

    __call__ = foo

fm = FooMaker()
f3 = fm()

f3.b<caret>