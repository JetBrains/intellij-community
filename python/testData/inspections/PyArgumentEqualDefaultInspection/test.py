def foo(a, b = 345, c = 1):
    pass

foo(1, <weak_warning descr="Argument equals to default parameter value">345</weak_warning>, 22)

def foo(a = None):
    pass
foo(<weak_warning descr="Argument equals to default parameter value">a = None</weak_warning>)

def bar(a = 2, b = 3):
  pass

#PY-3260
bar(<weak_warning descr="Argument equals to default parameter value">a = 2</weak_warning>, <weak_warning descr="Argument equals to default parameter value">b = 3</weak_warning>)

class A:
  @classmethod
  def foo(cls, a = 1):
    pass

a = A()

a.foo(<weak_warning descr="Argument equals to default parameter value">1</weak_warning>)


class C(object):
    def __init__(self):
        self._x = None

    def getx(self):
        return self._x
    def setx(self, value):
        self._x = value
    def delx(self):
        del self._x

    x = property(getx, None, fdel = delx, doc = "I'm the 'x' property.")


# PY-3455
import optparse

class Option(optparse.Option):
    pass

class OptionParser(optparse.OptionParser):
  def __init__(self):
        optparse.OptionParser.__init__(self, option_class=Option)

##

def bar(a = "qwer"):
  pass

bar(<weak_warning descr="Argument equals to default parameter value">a = 'qwer'</weak_warning>)

getattr(bar, "__doc__", None) # None is not highlighted

class a:
  def get(self, a, b = None):
    pass

kw = a()

kw['customerPaymentProfileId'] = kw.get("customerPaymentProfileId",
                                                 <weak_warning descr="Argument equals to default parameter value">None</weak_warning>)

{1: 2}.get('foo', None) #pass
{1: 2}.pop('foo', None) #pass

def a(foo=1*1024):
    print foo

a( 1024*1024)


def f1(value=-1):
    print(value)

def f2():
    f1(value =-2)

def decorator(arg='baz'):
    return lambda x: x

@decorator(<weak_warning descr="Argument equals to default parameter value">arg='baz'</weak_warning>)
def f<error descr="'(' expected">:</error>
  pass