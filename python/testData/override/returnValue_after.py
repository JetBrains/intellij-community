class A:
    def doStuff(self, foo=True): return True

class B(A):
    def doStuff(self, foo=True):
        <selection>return super().doStuff(foo)</selection>

    def otherMethod(self, foo, bar):
        print foo, bar
