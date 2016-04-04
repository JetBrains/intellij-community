class Foo(object):
    bar = True

class FooMaker(object):
    def __call__(self):
        return Foo()

fm = FooMaker()
f3 = fm()

f3.bar