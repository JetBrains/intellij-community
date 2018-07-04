class A1:
    foo = <error descr="Unresolved reference 'B1'">B1</error>()

class B1:
    pass


class A21:
    class A22:
        bar = <error descr="Unresolved reference 'B2'">B2</error>()

class B2:
    pass


class A31:
    def baz(self):
        class A32:
            egg = B3()

class B3:
    pass