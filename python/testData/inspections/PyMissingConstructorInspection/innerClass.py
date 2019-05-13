
class Base(object):
  def __init__(self, param):
    print "Base", param


class Wrapper(object):
  class Child(Base):
    def __init__(self, param1, param2):
      # Here PyCharm claims no super call
      super(Wrapper.Child, self).__init__(param2)
      print "Child", param1

  def __init__(self):
    self.child = self.Child("aaa", "bbb")


wrapper = Wrapper()
