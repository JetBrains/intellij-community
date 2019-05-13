# Python 2.6+

class A(object):
  
  @property
  def w1(self):
    return self._v

  @w1.setter
  def w1(self, v):
    self._v = v

  @w1.deleter
  def w1(self):
    pass


  @property
  def w2(self):
    "doc of w2"
    return self._v

  @w2.setter # inefficient
  def wXXX(self, v):
    self._v = v

  # deleter absent
