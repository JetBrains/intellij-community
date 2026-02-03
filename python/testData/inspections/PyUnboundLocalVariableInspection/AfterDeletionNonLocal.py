def foo():
  var = "local"

  def bar():
    nonlocal var
    print(var)
    del var
    print(<warning descr="Local variable 'var' might be referenced before assignment">var</warning>)