class Clzz:
  def b(self):
    return Clzz()

  def c(self):
    return "123"


a = Clzz()
a.b().c().<caret>