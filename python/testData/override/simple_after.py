class A:
    def doStuff(self): pass

class B(A):
    def doStuff(self):
        <selection>A.doStuff(self)</selection>

    def otherMethod(self, foo, bar):
        print foo, bar
