def <info descr="PY.FUNC_DEFINITION">outer</info>():
  <info descr="PY.LOCAL_VARIABLE">x</info> = "John"
  def <info descr="PY.NESTED_FUNC_DEFINITION">inner</info>():
    nonlocal x
    x = "hello"
  <info descr="PY.FUNCTION_CALL">inner</info>()
  return <info descr="PY.LOCAL_VARIABLE">x</info>