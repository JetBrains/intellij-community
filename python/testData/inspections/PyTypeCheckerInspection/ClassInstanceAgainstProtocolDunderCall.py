from typing import Protocol

class C:
    def __call__(self, a: undef):
        pass

class P(Protocol):
    def __call__(self, a: undef):
        pass

def foo(arg: P):
    pass

foo(C())
foo(<warning descr="Expected type 'P', got 'type[C]' instead">C</warning>)