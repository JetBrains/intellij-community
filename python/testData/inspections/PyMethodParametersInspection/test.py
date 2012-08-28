def foo(bar):
  pass # nothing

def __call__(foo):
  pass # nothing, out of class

def innocent(f):
  "innocent deco"
  return f

class Foo(object): 

  def __init__(<weak_warning descr="Usually first parameter of a method is named 'self'">a</weak_warning>, b):
    pass # rename a

  def foo<error descr="Method must have a first parameter, usually called 'self'">()</error>:
    pass # propose self

  def loo<weak_warning descr="First parameter of a non-static method must not be a tuple">((l, g), *rest)</weak_warning>:
    pass # complain at tuple

  def zoo(*animals):
    pass # nothing

  def __new__(<weak_warning descr="Usually first parameter of such methods is named 'cls'">self</weak_warning>):
    pass # propose cls

  @classmethod
  def boo(<weak_warning descr="Usually first parameter of such methods is named 'cls'">self</weak_warning>):
    pass # propose cls

  @classmethod
  @innocent
  def boo(<weak_warning descr="Usually first parameter of such methods is named 'cls'">self</weak_warning>):
    pass # propose cls

  @innocent
  @classmethod
  def boo(<weak_warning descr="Usually first parameter of such methods is named 'cls'">self</weak_warning>):
    pass # propose cls

  @staticmethod
  def moo(a):
    pass # nothing

  @staticmethod
  def qoo((x, y, z), t):
    pass # nothing

  @staticmethod
  @innocent
  def qoo((x, y, z), t):
    pass # nothing

  @innocent
  @staticmethod
  def qoo((x, y, z), t):
    pass # nothing

class Meta(type):
  
  def foo(<weak_warning descr="Usually first parameter of such methods is named 'self'">first</weak_warning>): # rename to "self"
    pass

  def __new__(<weak_warning descr="Usually first parameter of such methods is named 'mcs'">self</weak_warning>, *rest): # rename to "mcs"
    pass

  def __call__(<weak_warning descr="Usually first parameter of such methods is named 'cls'">self</weak_warning>): # rename to "cls"
    pass

  def bar(cls): # <- rename to "self"
    return "foobar"

  @classmethod
  def baz(<weak_warning descr="Usually first parameter of such methods is named 'mcs'">moo</weak_warning>): # <- rename to "mcs"
    return "foobar"

  @staticmethod
  def bazz(param1):
    return "foobar"
