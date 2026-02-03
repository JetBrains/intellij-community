__author__ = 'ktisha'

class Base:
    def __init__(self):
        self.param = 2

class Child(Base):
    def __init__(self):
        Base.__init__(self)
        self.my = 2

    def f(self):
        pass