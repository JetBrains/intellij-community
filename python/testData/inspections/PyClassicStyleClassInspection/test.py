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


# PY-28150
from typing import Generic, TypeVar
T = TypeVar("T")
class A(Generic[T]):
    pass
class B(A):
    pass
