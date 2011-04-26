""" test docstring inspection"""
def foo1(a, b):
  """

  @param a:
  @param b:
  @return:
  """
  pass

def foo(a, b, n):
  <warning descr="Missing parameters b, n in docstring.">"""

  @param a:
  @return:
  """</warning>
  pass

def foo():
  <warning descr="Unexpected parameters a in docstring.">"""

  @param a:
  @return:
  """</warning>
  pass


def foo(a, c):
  <warning descr="Missing parameters c in docstring. Unexpected parameters b in docstring.">"""
  @param a:
  @param b:
  @return:
  """</warning>
  pass