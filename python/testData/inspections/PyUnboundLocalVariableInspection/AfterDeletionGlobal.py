var = "global"

def foo():
  global var
  print(var)
  del var
  print(<warning descr="Name 'var' can be undefined">var</warning>)