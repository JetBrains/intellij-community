__author__ = 'ktisha'

class C(object):
    def __init__(self):
        self._x = None

    @property
    def x(self):
        """I'm the 'x' property."""
        return "property"

    @x.setter
    def x(self, value):
        print "setter"

    @x.deleter
    def x(self):
        print "deleter"

