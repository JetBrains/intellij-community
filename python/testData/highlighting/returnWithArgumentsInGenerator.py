def <info descr="PY.FUNC_DEFINITION">f</info>():
  yield 42
  <error descr="Python versions < 3.3 do not allow 'return' with argument inside generator.">return 28</error>
