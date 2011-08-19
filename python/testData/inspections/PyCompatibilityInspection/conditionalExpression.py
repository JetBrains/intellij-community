# PY-4293
def test_conditional_expression(val):
  x = <warning descr="Python version 2.4 doesn't support this syntax.">'Yes' if val else 'No'</warning>
  return <warning descr="Python version 2.4 doesn't support this syntax.">'Yes' if val else 'No'</warning>

def f(arg):
  pass

f(<warning descr="Python version 2.4 doesn't support this syntax.">1 if True else 2</warning>)