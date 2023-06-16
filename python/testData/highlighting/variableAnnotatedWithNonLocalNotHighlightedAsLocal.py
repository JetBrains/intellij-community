def <info descr="PY.FUNC_DEFINITION">outer</info>():
  x = "John"
  def <info descr="PY.NESTED_FUNC_DEFINITION">inner</info>():
    nonlocal x
    x = "hello"
  <info descr="PY.FUNCTION_CALL">inner</info>()
  return x