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


# PY-19701
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        return self._myprop

    def set_myprop(self, val):
        def inner_func(n):
            return n
        self._myprop = inner_func(val)

    myprop = property(get_myprop, set_myprop)  # pass


# all flows have exit point
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        if a > b:
            return self._myprop
        elif a < b:
            raise self._myprop
        else:
            yield self._myprop

    myprop = property(get_myprop)  # pass


# some flows have not exit point
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        if a > b:
            return self._myprop
        elif a < b:
            raise self._myprop

    myprop = property(get_myprop)  # pass


# some flows have not exit point
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        if a > b:
            return self._myprop

    myprop = property(get_myprop)  # pass


# non-empty for
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        for i in range(5):
            yield i

    myprop = property(get_myprop)  # pass


# empty for
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        for i in []:
            yield i

    myprop = property(get_myprop)  # shouldn't pass with better analysis, pass at the moment


# non-empty while
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        i = 0
        while i < 5:
            yield i
            i += 1

    myprop = property(get_myprop)  # pass


# empty while
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        while False:
            yield i

    myprop = property(get_myprop)  # shouldn't pass with better analysis, pass at the moment


# non-empty while with two conditions
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        i = 0
        j = 0
        while i < 5 and j == 0:
            yield i
            i += 1

    myprop = property(get_myprop)  # pass


# empty while with two conditions
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        i = 0
        j = 0
        while i > 5 and j == 0:
            yield i

    myprop = property(get_myprop)  # shouldn't pass with better analysis, pass at the moment


# setter has exit point
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        return self._myprop

    def set_myprop(self, val):
        self._myprop = val
        return 10

    myprop = property(get_myprop, set_myprop)  # shouldn't pass


# setter has exit point
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        return self._myprop

    def set_myprop(self, val):
        self._myprop = val
        yield 10

    myprop = property(get_myprop, set_myprop)  # shouldn't pass


# setter has raise statement
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        return self._myprop

    def set_myprop(self, val):
        self._myprop = val
        raise NotImplementedError()

    myprop = property(get_myprop, set_myprop)  # pass


# setter has exit point in some flow
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        return self._myprop

    def set_myprop(self, val):
        self._myprop = val
        if a > b:
            return 10

    myprop = property(get_myprop, set_myprop)  # shouldn't pass


# setter has exit point in some flow
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        return self._myprop

    def set_myprop(self, val):
        self._myprop = val
        if a > b:
            yield 10

    myprop = property(get_myprop, set_myprop)  # shouldn't pass


# setter has raise statement in some flow
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        return self._myprop

    def set_myprop(self, val):
        self._myprop = val
        if a > b:
            raise NotImplementedError()

    myprop = property(get_myprop, set_myprop)  # pass
