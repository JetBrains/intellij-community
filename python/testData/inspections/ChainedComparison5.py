mapsize = 35

def test(x, y):
  if <weak_warning descr="Simplify chained comparison">0 <= x < <caret>mapsize and y >= 0 and y < mapsize</weak_warning>:
    return 1