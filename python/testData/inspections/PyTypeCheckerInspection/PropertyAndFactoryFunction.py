class C(object):
    @property
    def foo(self):
        return 'bar'

def f():
    return C()

def test():
    f().foo + <warning descr="Expected type 'AnyStr ≤: Union[str, unicode]', got 'Literal[1]' instead">1</warning>
