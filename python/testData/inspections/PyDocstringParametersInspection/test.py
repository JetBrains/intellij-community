""" test docstring inspection"""
def foo1(a, b):
  """

  @param a:
  @param b:
  @return:
  """
  pass

def foo(a, <warning descr="Missing parameter b in docstring">b</warning>, <warning descr="Missing parameter n in docstring">n</warning>):
  """

  @param a:
  @return:
  """
  pass

def foo():
  """

  @param <warning descr="Unexpected parameter a in docstring">a</warning>:
  @return:
  """
  pass


def foo(a, <warning descr="Missing parameter c in docstring">c</warning>):
  """
  @param a:
  @param <warning descr="Unexpected parameter b in docstring">b</warning>:
  @return:
  """
  pass