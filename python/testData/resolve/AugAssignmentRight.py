class A:
    pass

class B:
    def __radd__(self, other):
        pass

A() += B()
    <ref>