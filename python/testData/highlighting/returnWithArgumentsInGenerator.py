def f():
  yield 42
  <error descr="'return' with argument inside generator">return 28</error>