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
  
  Parameters:
    a:
    <weak_warning descr="Unexpected parameter b in docstring">b</weak_warning>:
  """
  pass
  
def varagrs_defined_without_stars(x, *args, y, **kwargs):
    """
    Args:
      x:
      args:
      y:
      kwargs:
    """
    
def varagrs_dont_exist():
  """
  Args:
    *<weak_warning descr="Unexpected parameter args in docstring">args</weak_warning>:
    **<weak_warning descr="Unexpected parameter kwargs in docstring">kwargs</weak_warning>:
  """
  
def varagrs_undefined(x, *args, y, **kwargs):
  """
  Args:
    x:
    y:
  """
  

def no_parameters_declared(x, y):
    """ 
    """
    
