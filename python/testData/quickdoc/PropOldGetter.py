class A(object):
  def __getX(self):
    "Doc of getter"
    return self.__x
  x = property(__getX)

A().<the_ref>x    