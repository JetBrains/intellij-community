class A[<weak_warning descr="Type parameter 'T' is not used">T</weak_warning>]:
    def f(self):
        pass

class B[S, <weak_warning descr="Type parameter 'T' is not used">T</weak_warning>]:
    def f(self, s: S):
        pass