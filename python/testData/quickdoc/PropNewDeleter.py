class A(object):
  @property
  def x(self):
    "Does things to X"
    return 1

  @x.deleter
  def x(self, v):
    "Deletes X"
    self.__x = None

del A().<the_ref>x