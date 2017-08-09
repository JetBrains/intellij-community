class <warning descr="Old-style class">A</warning>:
    def foo(self):
        pass

class <warning descr="Old-style class, because all classes from whom it inherits are old-style">B</warning>(A):
    pass

class C(A, B, object):
    pass

class D(C):
    pass

class E:
    __metaclass__ = None


def create_meta():
    return type

Meta = create_meta()

class Something(Meta):
    pass


class DerivedException(Exception):
    pass


# PY-25560
from six import add_metaclass, with_metaclass

class Concrete(with_metaclass(type)):
    pass
@add_metaclass(type)
class C:
    pass

def metaclass():
    class _metaclass(type):
        pass
    return _metaclass
class Concrete(with_metaclass(metaclass())):
    pass
@add_metaclass(metaclass())
class C:
    pass