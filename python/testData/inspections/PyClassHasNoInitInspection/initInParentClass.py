__author__ = 'ktisha'
class Base(object):
    def __init__(self):
        self.my = 1


class Child(Base):   # <- Child class here should not be highlighted since
# it has init mehtod of Base class.
    pass
