def deco(param):
  pass

@deco # ok
def f6():
  pass

@deco(1) # ok
def f7():
  pass

@deco(<warning descr="Parameter 'param' unfilled">)</warning> # fail: missing param
def f8():
  pass

@deco(1, <warning descr="Unexpected argument">2</warning>) # fail: extra param
def f9():
  pass

def deco2(p1, p2): pass

<warning descr="Parameter 'p2' unfilled">@deco2</warning> # fail: missing p2
def f10():
  pass

@deco2(1<warning descr="Parameter 'p2' unfilled">)</warning> # fail: missing p2
def f11():
  pass

@deco2(1, 2) # ok
def f12():
  pass

@deco2(p2=2, p1=1) # ok
def f13():
  pass


class Dec:
  def __init__(self, param):
    pass

@Dec # ok
def f14():
  pass

@Dec(param=1) # ok
def f15():
  pass

@Dec(<warning descr="Parameter 'param' unfilled">)</warning> # fail: missing param
def f16():
  pass


class Dec2:
  def __init__(self, p1, p2):
    pass

@Dec2(<warning descr="Parameter 'p1' unfilled"><warning descr="Parameter 'p2' unfilled">)</warning></warning> # fail: no p1, p2
def f17():
  pass

<warning descr="Parameter 'p2' unfilled">@Dec2</warning> # fail: no p2
def f18():
  pass

@Dec2(1, 2) # ok
def f19():
  pass
