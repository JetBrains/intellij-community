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
  x2 = property(<warning descr="Getter should return or yield something"><warning descr="Getter signature should be (self)">__setX</warning></warning>)
  x3 = property(__getX, <warning descr="Setter should not return a value"><warning descr="Setter signature should be (self, value)">__getX</warning></warning>)
  x4 = property(__getX, fdel=<warning descr="Deleter should not return a value">__getX</warning>)
  x5 = property(__getX, doc=<warning descr="The doc parameter should be a string">123</warning>)

  x6 = property(lambda self: self._x)
  x7 = property(lambda self: self._x, <warning descr="Setter should not return a value"><warning descr="Setter signature should be (self, value)">lambda self: self._x</warning></warning>)

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
  def <warning descr="Getter should return or yield something">moo</warning>(self):
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
  x = property(gb)
  y = property(Ghostbusters())
  z = property(Ghostbusters)

class Eternal(object):
  def give(self):
    while True:
      yield 1

  def giveAndTake(self):
    x = 1
    while True:
      x = (yield x)

  one = property(give)
  anything = property(giveAndTake)
