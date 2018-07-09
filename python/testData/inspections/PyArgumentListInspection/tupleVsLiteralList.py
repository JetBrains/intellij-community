def f20(a, (b, c)):
  pass

f20(1, [2, 3]) # ok
f20(1, (2, 3, <warning descr="Unexpected argument">4</warning>)) # fail
