class A:
  def get_a(self):
      pass
  def foo(self):
    <error descr="Unresolved reference 'get_a'">g<caret>et_a</error>()
