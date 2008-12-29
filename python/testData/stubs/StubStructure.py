def deco(fun):
  return fun # valid

class FooClass:
  staticField = 1
  def __init__(self):
    self.instanceField = 2

  @deco
  def fooFunction(fooParam1, fooParam2) :
    pass

def topLevelFunction(tlfp1, tlfp2) :
  pass
  
top1 = 1
if True:
  top2 = 2
