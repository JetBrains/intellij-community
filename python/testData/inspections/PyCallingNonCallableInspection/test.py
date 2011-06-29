<warning descr="'tuple' object is not callable">(1,2)()</warning>

class SM(object):
    def my_method(): pass
    my_method = staticmethod(my_method)

    def q(self):
        self.my_method()

def concealer():
    class prog(object):
        def __call__(self): pass

    pr = prog()
    pr()

def test_module():
    import sys
    <warning descr="'sys' module is not callable">sys()</warning>
