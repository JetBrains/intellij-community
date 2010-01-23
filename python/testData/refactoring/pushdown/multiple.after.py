class Foo:
    

class Zope(Foo):
    def foo(self):
        print("a")

    def _mine(self):
       print "zope"

class Boo(Foo):
    def foo(self):
        print("a")

    def boo(self):
        print "rrrrr"
