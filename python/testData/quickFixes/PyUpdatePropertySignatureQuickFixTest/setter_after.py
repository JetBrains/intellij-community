class A(Aa):
    @property
    def x(self, r):
        return r

    @x.setter
    def x(self, r):
        self._x = ""
