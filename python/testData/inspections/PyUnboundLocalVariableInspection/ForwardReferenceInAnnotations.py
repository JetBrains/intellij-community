from __future__ import annotations

def f(a: A): # Should not produce "Name 'A' can be not defined" warning
    pass

class A:
    pass
