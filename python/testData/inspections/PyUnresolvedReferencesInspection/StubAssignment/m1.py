class C(object):
    def __init__(self):
        self.foo = -1

    def __mul__(self, other):
        """
        :rtype: C
        """
        return self

    __rmul__ = __mul__