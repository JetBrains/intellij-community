from typing import Protocol

class C:
    def __init__(self, a: undef):
        pass

class P(Protocol):
    def __call__(self, a: undef) -> C:
        pass

def foo(arg: P):
    pass

foo(C)
foo(<warning descr="Expected type 'P', got 'C' instead">C()</warning>)