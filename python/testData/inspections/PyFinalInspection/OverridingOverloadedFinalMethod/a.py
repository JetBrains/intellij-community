from b import A

class B(A):
    def <warning descr="'b.A.foo' is marked as '@final' and should not be overridden">foo</warning>(self, a):
        pass