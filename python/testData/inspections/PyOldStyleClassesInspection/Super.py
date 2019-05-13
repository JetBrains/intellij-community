
class AA:
  def __init__(self):
    <warning descr="Old-style class contains call for super method">super</warning>(AA, self)

class D:
  def __init__(self):
    <warning descr="Old-style class contains call for super method">super</warning>(self.__class__, self).__init__()

class B(object):
  def __init__(self):
    super(B, self)

