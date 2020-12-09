class C:
  @classmethod
  def foo(self):
    pass

<warning descr="Decorator @classmethod on a method outside the class"><caret>@classmethod</warning>
def foo(self):
  print ("Constructor C was called")