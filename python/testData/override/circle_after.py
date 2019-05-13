class Spam(Eggs):
  def spam_methods(self):
    pass

class Eggs(Spam):
  def spam_methods(self):
      super(Eggs, self).spam_methods()

  def my_methods(self):
    pass
