class Foo(object):
    def __init__(self, color):
        self.color = color

class Bar(object):
    fooFactory = Foo

    def quux(self):
        foo = self.fooFactory("orange") # ok

class Foo:
    def __init__(self, name):
        print name
class Bar(Foo):
    def __init__(self, name):
        Foo.__init__(self, name)
Foo("Foo") # pass
