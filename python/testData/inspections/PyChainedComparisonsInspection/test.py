<weak_warning descr="Simplify chained comparison">a < b and b < c</weak_warning>
<weak_warning descr="Simplify chained comparison">b > a and b < c</weak_warning>
<weak_warning descr="Simplify chained comparison"><weak_warning descr="Simplify chained comparison">a < b and b < c</weak_warning> and c < d</weak_warning>
if <weak_warning descr="Simplify chained comparison">a < b and b < c</weak_warning>:
    pass
q = <weak_warning descr="Simplify chained comparison">a < c and c < d < e</weak_warning>
<weak_warning descr="Simplify chained comparison"><weak_warning descr="Simplify chained comparison">a < c and b < e and e < f</weak_warning> and a < b and b < c</weak_warning>

result = <weak_warning descr="Simplify chained comparison">a < c and c == 4</weak_warning>
q = <weak_warning descr="Simplify chained comparison">a < b < c and c <= d</weak_warning>
q = <weak_warning descr="Simplify chained comparison">a >= b >= c and c > d</weak_warning>

#PY-3126
if <weak_warning descr="Simplify chained comparison">b > a and b < c</weak_warning>:
  print ("a")

if c > a and b < c:
  print("b")

if <weak_warning descr="Simplify chained comparison">a > c and b < c</weak_warning>:
  print("d")

if <weak_warning descr="Simplify chained comparison">b >= a > e and b < c</weak_warning>:
  print "q"

#PY-4624
if a > b and c == b - a:
  pass