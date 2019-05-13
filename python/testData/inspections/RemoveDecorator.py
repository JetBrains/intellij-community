class C:
  @classmethod
  def foo(self):
    pass

<warning descr="Decorator @classmethod on method outside class"><caret>@classmethod</warning>
def foo(self):
  print ("Constructor C was called")