<warning descr="Statement seems to have no effect">2 + 3</warning>
<warning descr="Statement seems to have no effect">string</warning>

def foo():
  <warning descr="Statement seems to have no effect">3</warning>

def bar():
  """a"""
  "a"

[allpats.extend(patlist) for patlist in pats.values()]

class Printer(object):
  def __lshift__(self, what):
    print what

cout = Printer()
cout << "Hello, world" # must not be reported

len(x) if True else len(y)

(foo())

foo(),

x = 3
""":type: int"""

x = 3
"""@type: int"""

foo()
"""fake docstring"""

def foo():
  y = 2
  """fake docstring"""

# PY-10755
def is_good(a):
    return a > 10

def do_something(a):
    print a

def process():
    for a in range(20):
        is_good(a) and do_something(a)
