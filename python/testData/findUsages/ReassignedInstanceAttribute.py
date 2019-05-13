class A(object):
    def __init__(self):
        self.bacaba = 1

    def foo(self, x):
        self.bacaba = x

class B(A):
    def __init__(self):
        super(B, self).__init__()
        self.bacaba = 2

    def foo2(self):
        self.ba<caret>caba = 3

    def foo3(self):
        print self.bacaba
  