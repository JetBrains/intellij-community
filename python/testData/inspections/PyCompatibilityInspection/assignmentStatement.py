# PY-2792
x = <warning descr="Python version 2.4 doesn't support this syntax.">True if condition else False</warning>

def foo():    # PY-2796
  <warning descr="Python version 2.4 doesn't support this syntax. In Python <= 2.4, yield was a statement; it didn't return any value.">a = (yield 1)</warning>
