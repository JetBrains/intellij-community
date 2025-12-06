class Common:
    def a(self):
        """Common.a doc."""
        pass

class A(Common):
    pass

class B(Common):
    pass

x: A | B
x.a<the_ref>
