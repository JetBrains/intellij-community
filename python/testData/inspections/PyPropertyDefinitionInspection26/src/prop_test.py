class A(object):
  def __init__(self):
    self._x = 1

  @property
  def foo(self):
    return self._x

  @foo.setter
  def foo(self, x):
    self._x = x

  @foo.deleter
  def foo(self):
    pass

  @property
  def boo(self):
    return self._x

  @boo.setter # name mismatch
  def boo1(self, x):
    self._x = x

  @boo.deleter # name mismatch
  def boo2(self):
    pass

  @property
  def moo(self): # should return
    pass

  @moo.setter
  def moo(self, x): # shouldn't return
    return 1

  @moo.deleter
  def moo(self): # shouldn't return
    return self._x

  @qoo.setter # unknown qoo is reported in ref inspection
  def qoo(self, v):
    self._x = v

  @property
  def futuroo(self):
    raise NotImplementedError("Override!") # ok though no return

  @property
  def futuroo(self):
    """Docstring."""
    raise NotImplementedError("Override!") # ok though no return

  @property
  def xoo(self):
    return self._x

  @xoo.setter
  def xoo(self, x):
    self._x = x
    return

  get_foo2 = lambda self: 'foo2'

  foo2 = property(get_foo2)

  @property
  @abstractproperty
  def abstract_property(self):
      pass
