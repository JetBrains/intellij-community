""" test docstring inspection"""
def foo1(a, b):
  """

  Parameters:
    a: foo
    b: bar
  """
  pass

def foo(a, <weak_warning descr="Missing parameter b in docstring">b</weak_warning>, <weak_warning descr="Missing parameter n in docstring">n</weak_warning>):
  """

  Parameters:
    a: foo
  """
  pass

def foo():
  """

  Parameters:
    <weak_warning descr="Unexpected parameter a in docstring">a</weak_warning>: foo
  """
  pass

def compare(a, b, *, key=None):
    """

    Parameters:
      a:
      b:
      key:
    """
    pass

def foo(a, <weak_warning descr="Missing parameter c in docstring">c</weak_warning>):
  """
  
  Params:
    a:
    <weak_warning descr="Unexpected parameter b in docstring">b</weak_warning>:
  """
  pass