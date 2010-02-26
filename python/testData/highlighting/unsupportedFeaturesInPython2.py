<warning descr="Dictionary comprehension is not supported in Python 2">{k: v for k, v in stuff}</warning>

try:
  pass
<warning descr="Python 2 does not support this syntax">except a as name:
  pass</warning>

class A(B):
  def foo(self):
    <warning descr="super() should have arguments in Python 2">super()</warning>

<warning descr="Python 2 does not support set literal expressions">{1, 2}</warning>