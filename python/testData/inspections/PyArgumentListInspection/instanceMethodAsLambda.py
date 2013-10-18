class TestFour():
  is_super = lambda self: True if self.__class__.__name__ == 'TestFour' else False

  def is_sub(self):
    return not self.is_super() # pass: implicit 'self'
