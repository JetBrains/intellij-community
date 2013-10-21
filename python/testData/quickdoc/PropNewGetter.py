class A(object):
  @property
  def x(self):
    <the_doc>"Does things to X"
    return 1

a = A()
a.<the_ref>x    
