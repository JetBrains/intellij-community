class A:
    def doStuff(self, foo=True): return True

class B(A):
    def otherMethod(self, foo, bar):
        print foo, bar
