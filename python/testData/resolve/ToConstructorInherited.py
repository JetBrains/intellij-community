# multi-resolve: to class and to inherited constructor
class Foo:
  def __init__(self):
    pass

class Bar(Foo):
  pass

Bar()
#<ref>