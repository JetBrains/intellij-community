__author__ = 'ktisha'

class Base:
    def __init__(self):
        self.param = 2

class Child(Base):
    def f(self):
        self.<caret>my = 2