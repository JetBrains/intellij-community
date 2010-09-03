class Moo(object):
  def __init__(self):
    "Doc of Moo()"
    pass


class Foo(Moo):
  # no doc to inherit
  def __init__(self):
    # no direct doc
    pass

<the_ref>Foo()
