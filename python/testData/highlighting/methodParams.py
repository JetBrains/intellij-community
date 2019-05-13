class A:
  def <error descr="Method must have a first parameter, usually called 'self'">foo1</error>(): pass
  
  def foo2(<warning descr="Did not you mean 'self'?">elf</warning>): pass
  
  def foo3<info descr="Usually first parameter of a method is named 'self'">(this)</info>: pass