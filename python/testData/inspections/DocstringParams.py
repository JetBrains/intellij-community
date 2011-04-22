""" file's docstring """
def foo(a, b, f):
  <warning descr="Missing parameters b, f in docstring. Unexpected parameters c in docstring.">"""
  some description<caret>
  another line of description

  @param a: some description
            of param
  @param c: another description
            of param
  @return:
  """</warning>
  pass