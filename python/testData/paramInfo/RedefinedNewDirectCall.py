# signature of overridden __new__

class A(object):
  def __new__(cls, a, b):
    pass

A.__new__(<arg1>A, <arg2>1, <arg3>2)
