class A:
  def unresolved(self):
    pass

class B:
  def unresolved(self):
    pass

def foo3(param):
  i = param.unreso<caret>lved()