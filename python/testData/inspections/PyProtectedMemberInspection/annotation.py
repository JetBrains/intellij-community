class A:
  pass

A._inner = 10
def id(a: A._inner):
  return a