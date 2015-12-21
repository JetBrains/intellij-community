""" test docstring inspection"""
def foo1(a, b):
  """

  @param a:
  @param b:
  @return:
  """
  pass

def foo(a, <weak_warning descr="Missing parameter b in docstring">b</weak_warning>, <weak_warning descr="Missing parameter n in docstring">n</weak_warning>):
  """

  @param a:
  @return:
  """
  pass

def foo():
  """

  @param <weak_warning descr="Unexpected parameter a in docstring">a</weak_warning>:
  @return:
  """
  pass

def compare(a, b, *, key=None):
    """

    :param a:
    :param b:
    :param key:
    :return:
    """
    pass

def foo(a, <weak_warning descr="Missing parameter c in docstring">c</weak_warning>):
  """
  @param a:
  @param <weak_warning descr="Unexpected parameter b in docstring">b</weak_warning>:
  @return:
  """
  pass
  
  
def varargs_undefined(x, *args, y, **kwargs):
    """
    @param x:
    @param y:
    """
    pass


def no_parameters_declared(x, y):
    """ 
    """
