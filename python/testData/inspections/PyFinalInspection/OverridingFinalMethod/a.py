from b import C
class D(C):
    def <warning descr="'b.C.method' is marked as '@final' and should not be overridden">method</warning>(self):
        pass