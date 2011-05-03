def foo(a, b = 345, c = 1):
    pass

foo(1, <warning descr="Argument equals to default parameter value">345</warning>, 22)

a = dict()
a.get(1, <warning descr="Argument equals to default parameter value">None</warning>)

def foo(a = None):
    pass
foo(<warning descr="Argument equals to default parameter value">a = None</warning>)

def bar(a = 2, b = 3):
  pass

#PY-3260
bar(<warning descr="Argument equals to default parameter value">a = 2</warning>, <warning descr="Argument equals to default parameter value">b = 3</warning>)

class A:
  @classmethod
  def foo(cls, a = 1):
    pass

a = A()

a.foo(<warning descr="Argument equals to default parameter value">1</warning>)


class C(object):
    def __init__(self):
        self._x = None

    def getx(self):
        return self._x
    def setx(self, value):
        self._x = value
    def delx(self):
        del self._x

    x = property(getx, <warning descr="Argument equals to default parameter value">None</warning>, fdel = delx, doc = "I'm the 'x' property.")


# PY-3455
import optparse

class Option(optparse.Option):
    pass

class OptionParser(optparse.OptionParser):
  def __init__(self):
        optparse.OptionParser.__init__(self, option_class=Option)