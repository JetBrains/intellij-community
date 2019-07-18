class A1:
    def m(self, p1, /):
        pass


class B1(A1):
    def m<warning descr="Signature of method 'B1.m()' does not match signature of base method in class 'A1'">(self, p1, p2)</warning>:
        pass


class A2:
    def baz(self, a, b):
        pass


class B2(A2):
    def baz<warning descr="Signature of method 'B2.baz()' does not match signature of base method in class 'A2'">(self, a, /)</warning>:
        pass