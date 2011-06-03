""" file's docstring """
def foo(a, <warning descr="Missing parameter b in docstring"><caret>b</warning>, <warning descr="Missing parameter f in docstring">f</warning>):
  """
  some description
  another line of description

  @param a: some description
            of param
  @param <warning descr="Unexpected parameter c in docstring">c</warning>: another description
            of param
  @return:
  """
  pass