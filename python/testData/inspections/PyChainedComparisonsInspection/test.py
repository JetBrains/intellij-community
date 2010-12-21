<warning descr="Simplify chained comparison">a < b and b < c</warning>
<warning descr="Simplify chained comparison"><warning descr="Simplify chained comparison">b > a and b < c and c < d</warning> and d < e</warning>
<warning descr="Simplify chained comparison"><warning descr="Simplify chained comparison">a < b and b < c</warning> and c < d</warning>
if <warning descr="Simplify chained comparison">a < b and b < c</warning>:
    pass
q = <warning descr="Simplify chained comparison">a < c and c < d < e</warning>
<warning descr="Simplify chained comparison"><warning descr="Simplify chained comparison">a < c and b < e and e < f</warning> and a < b and b < c</warning>

result = <warning descr="Simplify chained comparison">a < c and c == 4</warning>
q = <warning descr="Simplify chained comparison">a < b < c and c <= d</warning>
q = <warning descr="Simplify chained comparison">a >= b >= c and c > d</warning>
