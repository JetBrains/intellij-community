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

  <warning descr="Names of function and decorator don't match; property accessor is not created">@boo.setter</warning>
  def boo1(self, x):
    self._x = x

  <warning descr="Names of function and decorator don't match; property accessor is not created">@boo.deleter</warning>
  def boo2(self):
    pass

  @property
  def <warning descr="Getter should return or yield something">moo</warning>(self):
    pass

  @moo.setter
  def <warning descr="Setter should not return a value">moo</warning>(self, x):
    return 1

  @moo.deleter
  def <warning descr="Deleter should not return a value">moo</warning>(self):
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

    myprop = property(get_myprop, set_myprop)


# all flows have exit point
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        if a > b:
            <error descr="Python versions < 3.3 do not allow 'return' with argument inside generator.">return self._myprop</error>
        elif a < b:
            raise self._myprop
        else:
            yield self._myprop

    myprop = property(get_myprop)


# some flows have not exit point
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        if a > b:
            return self._myprop
        elif a < b:
            raise self._myprop

    myprop = property(get_myprop)


# some flows have not exit point
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        if a > b:
            return self._myprop

    myprop = property(get_myprop)


# non-empty for
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        for i in range(5):
            yield i

    myprop = property(get_myprop)


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

    myprop = property(get_myprop)


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

    myprop = property(get_myprop)


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

    myprop = property(get_myprop, <warning descr="Setter should not return a value">set_myprop</warning>)


# setter has exit point
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        return self._myprop

    def set_myprop(self, val):
        self._myprop = val
        yield 10

    myprop = property(get_myprop, <warning descr="Setter should not return a value">set_myprop</warning>)


# setter has raise statement
class Test(object):
    def __init__(self):
        self._myprop = None

    def get_myprop(self):
        return self._myprop

    def set_myprop(self, val):
        self._myprop = val
        raise NotImplementedError()

    myprop = property(get_myprop, set_myprop)


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

    myprop = property(get_myprop, <warning descr="Setter should not return a value">set_myprop</warning>)


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

    myprop = property(get_myprop, <warning descr="Setter should not return a value">set_myprop</warning>)


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

    myprop = property(get_myprop, set_myprop)
