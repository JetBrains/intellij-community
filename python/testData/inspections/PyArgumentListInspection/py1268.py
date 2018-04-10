def f(a, b, c):
  pass

f(c=1, *(10, 20))
f(*(10, 20), c=1)
f(*(10, 20, 30), <warning descr="Unexpected argument">c=1</warning>) # fail: duplicate c
f(1, *(10, 20, <warning descr="Unexpected argument">30</warning>)) # fail: tuple too long
f(1, <warning descr="Expected an iterable, got int">*(10)</warning>) # fail: wrong type
f(1, *(10,)<warning descr="Parameter 'c' unfilled">)</warning> # fail: tuple too short, c not mapped

def f1(a, b, c=1):
  return a,b,c

f1(c=3, *(1, 2))

def f2(a, b, c=1, *d):
  return a,b,c,d

f2(c=3, *(1,2))
f2(1,2,3, *(1,2))
f2(*(1,2), c=20)
f2(*(1,2), <error descr="Python versions < 3.5 do not allow positional arguments after *expression">20</error>) # fail: positional past *

def f3(a=1, b=2, c=3, *d):
  return a,b,c,d

f3(c=3, <warning descr="Unexpected argument">a=1</warning>, <warning descr="Unexpected argument">b=2</warning>, *(1,2)) # fail: a and b twice
f3(1, 2, *(3,), <warning descr="Unexpected argument">c=4</warning>) # fail: c twice
f3(1,2,3, *(1,2))
f3(c=3, *(1,2)) #
f3(1, <warning descr="Unexpected argument">c=3</warning>, *(1,2)) # fail: c twice
f3(c=3, a=1, b=2, <warning descr="Unexpected argument">d=(1,2)</warning>) # fail: unexpected d
f3(1, c=3, *(10,)) # ZZZ
f3(1, *(10,))
f3(1, *(10,), c=20)
f3(*(1,2), c=20)
f3(*(1,2), <warning descr="Unexpected argument">a=20</warning>) # fail: a twice
