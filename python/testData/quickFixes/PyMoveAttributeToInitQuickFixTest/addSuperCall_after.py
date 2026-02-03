__author__ = 'ktisha'

class Base(object):
    def __init__(self):
        self.param = 2

class Child(Base):
    def __init__(self):
        super(Child, self).__init__()
        self.my = 2

    def f(self):
        pass