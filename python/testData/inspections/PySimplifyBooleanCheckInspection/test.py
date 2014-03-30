if <weak_warning descr="Expression can be simplified">a == True</weak_warning>:
  if <weak_warning descr="Expression can be simplified">b == False</weak_warning>:
    if <weak_warning descr="Expression can be simplified">c != True</weak_warning>:
      if <weak_warning descr="Expression can be simplified">d != False</weak_warning>:
        pass
if a is False:
  pass
var = a is not True

# PY-6876
if a == 0:
    pass
