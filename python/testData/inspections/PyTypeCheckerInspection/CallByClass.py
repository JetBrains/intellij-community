class Z:
    def method(self):
          pass

class A:
    def method(self):
        Z.method(<warning descr="Expected type 'Z' (matched generic type 'Self@Z'), got 'Self@A' instead">self</warning>) # passing wrong instance
        Z.method(<warning descr="Expected type 'Z' (matched generic type 'Self@Z'), got 'Type[Z]' instead">Z</warning>) # passing class instead of instance
        Z.method(<warning descr="Expected type 'Z' (matched generic type 'Self@Z'), got 'Type[A]' instead">A</warning>) # passing class instead of instance AND wrong class
        Z.method(<warning descr="Expected type 'Z' (matched generic type 'Self@Z'), got 'A' instead">A()</warning>) # passing class instead of instance AND wrong class
        Z.method(Z()) #pass

    def __init__(self):
        pass

class B(A):
    def __init__(self):
        A.__init__(self) # pass

A.method(B())