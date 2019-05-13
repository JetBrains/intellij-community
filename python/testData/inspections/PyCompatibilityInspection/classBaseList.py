class Foo(base1, base2, <error descr="This syntax available only since py3">metaclass=mymeta</error>):
  pass

class Foo(base1, base2):
  pass

def foo(base1, base2, metaclass=mymeta):
  pass