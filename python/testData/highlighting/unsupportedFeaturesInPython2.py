from __future__ import with_statement

<warning descr="Dictionary comprehension is not supported in Python 2">{k: v for k, v in stuff}</warning>

try:
  pass
<warning descr="This Python version does not support this syntax">except a as name:
  pass</warning>

class A(B):
  def foo(self):
    <warning descr="super() should have arguments in Python 2">super()</warning>

<warning descr="Python version 2.5 does not support set literal expressions">{1, 2}</warning>

<warning descr="Python 2 does not support star expressions">*b</warning>, c = 1, 2, 3, 4, 5

if <warning descr="<> is deprecated, use != instead">a <> 2</warning>:
    pass

(<warning descr="Python version 2.5 does not support set comprehensions">{i for i in range(3)}</warning>)

with A() as a, <warning descr="Python version 2.5 does not support multiple context managers">B() as b</warning>:
    pass