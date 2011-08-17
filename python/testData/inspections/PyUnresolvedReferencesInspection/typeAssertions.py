# PY-2308
def f(x):
  if isinstance(x, list):
    x = []
  elif isinstance(x, dict):
    return x.items() #pass
