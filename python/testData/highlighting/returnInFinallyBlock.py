def foo():
  try:
    pass
  finally:
    <error descr="Python version 3.14 does not support 'return' inside 'finally' clause">return</error>