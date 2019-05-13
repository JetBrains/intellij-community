class A(Aa):
    @property
    def <warning descr="Getter signature should be (self)">x<caret></warning>():
        return ""

    @x.setter
    def <warning descr="Setter should not return a value">x</warning>(self, r):
        return r
