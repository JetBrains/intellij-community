otherworldly = property(lambda self: sum(self.items), doc="otherworldly")

class A(object):

  def getter(self):
    return self._v

  def setter(self, v):
    self._v = v

  def deleter(self):
    pass

  def v5getter(self):
      return self._v

  def v5setter(self, v):
      self._v = v

  v1 = property(getter, setter)
  v2 = property(fset=setter, fdel=deleter, fget=getter, doc="doc of v2")
  v3 = property(lambda self: 0, None, (deleter))
  v4 = otherworldly # NOTE: not supported yet
  v5 = property(v5getter, v5setter)
