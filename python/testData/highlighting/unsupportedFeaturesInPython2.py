<warning descr="Dictionary comprehension not supported in Python2">{k: v for k, v in stuff}</warning>

try:
  pass
<warning descr="Python2 not supported such syntax">except a as name:
  pass</warning>

class A(B):
  def foo(self):
    <warning descr="super() should have arguments in Python2">super()</warning>

<warning descr="Python2 not supported set literal expressions">{1, 2}</warning>