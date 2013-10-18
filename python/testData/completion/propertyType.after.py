class C(object):
  def __init__(self):
    self._x = []

  @property
  def x(self):
    return self._x

c = C()
c.x.append()

