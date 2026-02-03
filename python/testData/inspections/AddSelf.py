class A:
  def foo<caret><error descr="Method must have a first parameter, usually called 'self'">()</error>: # Add 'self'
    pass
