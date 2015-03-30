class C(object):
    @property
    def foo(self):
        return 'bar'

def f():
    return C()

def test():
    f().foo + <warning descr="Expected type 'Union[str, unicode]', got 'int' instead">1</warning>
