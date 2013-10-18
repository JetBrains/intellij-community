""" file's docstring """
def foo(a, <weak_warning descr="Missing parameter b in docstring">b</weak_warning>, <weak_warning descr="Missing parameter f in docstring">f</weak_warning>):
  """
  some description
  another line of description

  @param a: some description
            of param
  @param <weak_warning descr="Unexpected parameter c in docstring"><caret>c</weak_warning>: another description
            of param
  @return:
  """
  pass