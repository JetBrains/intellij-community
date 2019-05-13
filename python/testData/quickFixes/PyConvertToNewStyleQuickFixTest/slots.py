class B: pass

class A(B):
    <warning descr="Old-style class contains __slots__ definition">__<caret>slots__</warning> = ""

    def <warning descr="Old-style class contains __getattribute__ definition">__getattribute__</warning>(self, item):
        pass

