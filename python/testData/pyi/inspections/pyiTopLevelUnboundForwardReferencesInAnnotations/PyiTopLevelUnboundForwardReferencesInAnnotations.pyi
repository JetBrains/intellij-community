def f(x: C) -> C: ... # Should not produce "Name 'C' can be not defined" warning

D = C

class C: ...

E = C
