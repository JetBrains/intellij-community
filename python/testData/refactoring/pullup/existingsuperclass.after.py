class Foo:
    def foo(self):
        print("a")

class Zope(Hand, Foo):
    def _mine(self):
       print "zope"

class Boo():
    def boo(self):
        print "rrrrr"