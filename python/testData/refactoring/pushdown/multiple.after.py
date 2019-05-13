class Foo:
    pass

class Zope(Foo):
    def _mine(self):
       print "zope"

    def foo(self):
        print("a")


class Boo(Foo):
    def boo(self):
        print "rrrrr"

    def foo(self):
        print("a")
