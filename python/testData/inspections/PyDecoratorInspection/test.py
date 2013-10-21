class C:
  @classmethod
  def foo(self):
    pass

<warning descr="Decorator @classmethod on method outside class">@classmethod</warning>
def foo(self):
  print ("Constructor C was called")