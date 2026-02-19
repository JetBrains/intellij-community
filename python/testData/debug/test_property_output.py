class A:

    def __init__(self):
        self._shape = 3

    @property
    def shape(self):
        print("called property")
        return self._shape

a = A()
a.foo # line for breakpoint