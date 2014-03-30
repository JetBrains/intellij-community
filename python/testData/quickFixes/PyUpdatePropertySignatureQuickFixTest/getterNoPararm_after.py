class A(Aa):
    @property
    def x(self):
        return ""

    @x.setter
    def x(self, r):
        return r
