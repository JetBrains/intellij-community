class A(object):
  def __init__(self, bar):
    self._x = 1 ; self._bar = bar

  def __getX(self):
    return self._x

  def __setX(self, x):
    self._x = x

  def __delX(self):
    pass

  x1 = property(__getX, __setX, __delX, "doc of x1")
  x2 = property(__setX) # should return
  x3 = property(__getX, __getX) # should not return
  x4 = property(__getX, fdel=__getX) # should not return
  x5 = property(__getX, doc=123) # bad doc

  x6 = property(lambda self: self._x)
  x7 = property(lambda self: self._x, lambda self: self._x) # setter should not return

  @property
  def foo(self):
    return self._x

  @foo.setter # ignored in 2.5
  def foo(self, x):
    self._x = x

  @foo.deleter # ignored in 2.5
  def foo(self):
    pass

  @property
  def boo(self):
    return self._x

  @boo.setter
  def boo1(self, x): # ignored in 2.5 
    self._x = x

  @boo.deleter
  def boo2(self): # ignored in 2,5 
    pass
 
  @property
  def moo(self): # should return
    pass

  @moo.setter
  def foo(self, x):
    return 1 # ignored in 2.5 

  @foo.deleter
  def foo(self):
    return self._x # ignored in 2.5 

  @qoo.setter # unknown qoo is reported in ref inspection
  def qoo(self, v):
    self._x = v

  @property
  def bar(self):
    return None

class Ghostbusters(object):
  def __call__(self):
    return "Who do you call?"

gb = Ghostbusters()

class B(object):
  x = property(gb) # pass
  y = property(Ghostbusters()) # pass
  z = property(Ghostbusters) # pass

class Eternal(object):
  def give(self):
    while True:
      yield 1

  def giveAndTake(self):
    x = 1
    while True:
      x = (yield x)

  one = property(give) # should pass
  anything = property(giveAndTake) # should pass
