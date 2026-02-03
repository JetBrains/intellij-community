from __future__ import print_function


class A:
    def __init__(self, a):
        self.a = a

    def __gt__(self, other):
        if (self.a > other.a):
            return True
        else:
            return False


if (A(2) > A(3) > A(1)):
    print("ob1 is greater than ob2")
else:
    print("ob2 is greater than ob1")
