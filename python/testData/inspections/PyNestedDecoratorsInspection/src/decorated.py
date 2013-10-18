def innocent(f):
  "A transparent deco"
  print("I'm innocent!")
  return f

class A(object):
  def f1(self): # nothing
    pass

  @innocent
  @innocent
  def f2(cls): # nothing
    pass

  @classmethod
  @innocent
  def f2(cls): # nothing
    pass

  @staticmethod
  @innocent
  def f4(): # nothing
    pass

  @innocent # warn
  @classmethod
  def f3(cls):
    pass

  @innocent # warn
  @staticmethod
  def f5():
    pass

  @classmethod # warn
  @staticmethod
  def f2(cls):
    pass

  @innocent  # warn
  @classmethod
  @innocent
  def f2(cls):
    pass

  @innocent
  @innocent  # warn
  @classmethod
  @innocent
  def f2(cls):
    pass
