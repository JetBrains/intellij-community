class A(object):
  def __getX(self, x):
    "Doc of getter"
    self.__x = x
  x = property(fset=__getX)

A().<the_ref>x = 1    