class A:
    def doStuff(self, foo=True): pass

class B(A):
    def doStuff(self, foo=True):
        <selection>A.doStuff(self, foo)</selection>

    def otherMethod(self, foo, bar):
        print foo, bar
