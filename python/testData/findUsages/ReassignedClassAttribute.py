class A(object):
    bacaba = 0
    def __init__(self):
        self.bacaba = 1

    def foo(self, x):
        self.bacaba = x


class B(A):
    bacaba = 2
    def __init__(self):
        super(B, self).__init__()
        self.bacaba = 3

    def foo2(self):
        print self.bac<caret>aba