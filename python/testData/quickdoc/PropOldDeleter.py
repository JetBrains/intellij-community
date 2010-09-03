 class A(object):
  def __getX(self):
    "Doc of getter"
    return self.__x
  x = property(fdel=__getX)

del A().<the_ref>x