class A(Aa):
    @property
    def x(self, r):
        return r

    @x.setter
    def x(self, value):
        self._x = ""
