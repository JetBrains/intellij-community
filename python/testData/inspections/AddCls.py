class A:
  @classmethod
  def foo<caret><error descr="Method must have a first parameter, usually called 'cls'">()</error>: # Add 'cls'
    pass
