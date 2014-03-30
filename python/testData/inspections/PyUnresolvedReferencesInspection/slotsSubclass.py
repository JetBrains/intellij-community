class A(object):
    __slots__ = ['a', 'b']
    def __init__(self):
        self.a = None # <- all ok here
        self.b = None # <- all ok here

class C(A):
    __slots__ = ['c', 'd']

    def __init__(self, c):
        super(C, self).__init__()
        self.c = c
        self.d = self.b
        if self.c:
            self.a = 10
