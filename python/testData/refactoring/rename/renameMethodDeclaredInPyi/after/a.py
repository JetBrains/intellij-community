class TestClass(object):

  def __someMethod(self):
    self.someOtherMethodRenamed()

  def someOtherMethodRenamed(self):
    # Try a rename refactor operation on this
    # method, the above call to the method is
    # not renamed.
    return
