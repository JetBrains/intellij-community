__author__ = 'ktisha'

class Base(object):
    def __init__(self):
        self.my = 1

class Child(Base):
    def __init__(self):
        super(Child, self).__init__()

    def f(self):
        self.my = 1