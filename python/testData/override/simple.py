class A:
    def doStuff(self, foo=True): pass

class B(A):
    def otherMethod(self, foo, bar):
        print foo, bar
