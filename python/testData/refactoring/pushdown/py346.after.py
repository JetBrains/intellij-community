class A:
    pass

class B(A):
    def meth_b1(self):
        pass
    def meth_b2(self):
        pass

    def meth_a1(self, name = {}):
        pass

    def meth_a2(self):
        pass


class D(A):
    def meth_d1(self):
        pass

    def meth_a1(self, name = {}):
        pass

    def meth_a2(self):
        pass