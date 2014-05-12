class A(object):
    def __init__(self):
        pass

class B(A):
    def __init__(self, **kwargs):
        super(B, self).__init__()