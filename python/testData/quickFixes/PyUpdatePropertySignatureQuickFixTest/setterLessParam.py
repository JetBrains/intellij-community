class A(Aa):
    @property
    def <warning descr="Getter signature should be (self)">x</warning>(self, r):
        return r

    @x.setter
    def <warning descr="Setter signature should be (self, value)">x<caret></warning>():
        self._x = ""
