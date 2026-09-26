class TestClass(object):

  def __someMethod(self):
    self.someOtherMethod()

  def some<caret>OtherMethod(self):
    # Try a rename refactor operation on this
    # method, the above call to the method is
    # not renamed.
    return
