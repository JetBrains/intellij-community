class Foo:
    def foo(self):
        print("a")

class Zope():
    def _mine(self):
       print "zope"

class Boo(Zope, Foo):
    def boo(self):
        print "rrrrr"