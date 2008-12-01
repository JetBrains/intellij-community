class A:
  def foo<error descr="Method must have a first parameter, usually called 'self'">()</error>: # Add 'self'
    pass
