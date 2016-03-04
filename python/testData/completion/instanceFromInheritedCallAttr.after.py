class Foo(object):
    bar = True

class FooMakerAnc(object):
    def __call__(self):
        return Foo()

class FooMaker(FooMakerAnc):
    pass

fm = FooMaker()
f3 = fm()

f3.bar