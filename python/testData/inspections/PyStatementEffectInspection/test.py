<warning descr="Statement seems to have no effect">2 + 3</warning>
<warning descr="Statement seems to have no effect">string</warning>

def foo():
  <warning descr="Statement seems to have no effect">3</warning>

def bar():
  """a"""
  <warning descr="Statement seems to have no effect">"a"</warning>

[allpats.extend(patlist) for patlist in pats.values()]

class Printer(object):
  def __lshift__(self, what):
    print what

cout = Printer()
cout << "Hello, world" # must not be reported

len(x) if True else len(y)

(foo())

foo(),
