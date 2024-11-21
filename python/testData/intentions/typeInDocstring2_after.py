def func1(x):
  """

  :rtype: Any
  """
  return x

def func2(x):
  y = func1(x.keys())
  return y.startswith('foo')