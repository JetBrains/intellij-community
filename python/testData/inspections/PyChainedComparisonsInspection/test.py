<warning descr="Simplify chained comparison">a < b and b < c</warning>
<warning descr="Simplify chained comparison">b > a and b < c</warning>
<warning descr="Simplify chained comparison"><warning descr="Simplify chained comparison">a < b and b < c</warning> and c < d</warning>
if <warning descr="Simplify chained comparison">a < b and b < c</warning>:
    pass
q = <warning descr="Simplify chained comparison">a < c and c < d < e</warning>
<warning descr="Simplify chained comparison"><warning descr="Simplify chained comparison">a < c and b < e and e < f</warning> and a < b and b < c</warning>

result = <warning descr="Simplify chained comparison">a < c and c == 4</warning>
q = <warning descr="Simplify chained comparison">a < b < c and c <= d</warning>
q = <warning descr="Simplify chained comparison">a >= b >= c and c > d</warning>

#PY-3126
if <warning descr="Simplify chained comparison">b > a and b < c</warning>:
  print ("a")

if <warning descr="Simplify chained comparison">c > a and b < c</warning>:
  print("b")

if <warning descr="Simplify chained comparison">a > c and b < c</warning>:
  print("d")

if <warning descr="Simplify chained comparison">b >= a > e and b < c</warning>:
  print "q"