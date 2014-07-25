class B: pass

class A(object, B):
    __slots__ = ""

    def __getattribute__(self, item):
        pass

