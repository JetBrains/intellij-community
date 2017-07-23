
class A(object):
    def __init__(self):
        a = 1

class C(A):
    def __init__(self):
        super(C, self).__init__()

    def foo(self):
        pass