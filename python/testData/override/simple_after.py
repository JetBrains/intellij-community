class A:
    def doStuff(self, foo): pass

class B(A):
    def doStuff(self, foo):
        <selection>A.doStuff(self, foo)</selection>

    def otherMethod(self, foo, bar):
        print foo, bar
