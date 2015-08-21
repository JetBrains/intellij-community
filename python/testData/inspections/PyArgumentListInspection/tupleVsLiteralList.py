def f20(a, (b, c)):
  pass

f20(1, [2, 3]) # ok
f20(1, (2, 3, 4)) # ok: ignore problems with arguments of tuple parameters
