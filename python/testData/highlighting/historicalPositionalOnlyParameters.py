def f1(a, <warning descr="Positional-only parameter follows parameter that is not positional-only">__b</warning>): pass
def f2(__b, a): pass
def f3(a, /, __b): pass
def f4(a, *, __b): pass
def f5(a, *args, __b): pass
def f6(a, __b__, __): pass

class A:
    def m1(self, __b): pass
    def m2(self, a, <warning descr="Positional-only parameter follows parameter that is not positional-only">__b</warning>): pass
    @classmethod
    def c1(cls, __b): pass
    @classmethod
    def c2(cls, a, <warning descr="Positional-only parameter follows parameter that is not positional-only">__b</warning>): pass
    @staticmethod
    def s1(__b): pass
    @staticmethod
    def s1(a, <warning descr="Positional-only parameter follows parameter that is not positional-only">__b</warning>): pass