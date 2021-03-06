# fail

<error descr="Cannot assign to function call">int(1)</error> = 1

<error descr="Cannot assign to literal">12</error> = 1

<error descr="Cannot assign to operator">1 + 21</error> = 12

result = <error descr="Cannot assign to operator">a < c and c</error> = 4

<error descr="Cannot assign to ()">()</error> = 123
[] = 1
[<error descr="Cannot assign to literal">1</error>] = 1
<error descr="Cannot assign to literal">{}</error> = 1
<error descr="Cannot assign to literal">{1, 2, 3}</error> = 1

(<error descr="Cannot assign to literal">1</error>,(<error descr="Cannot assign to literal">2</error>, <error descr="Cannot assign to literal">3</error>)) = 3,(4,5)
del <error descr="Cannot delete literal">1</error>
del <error descr="Cannot delete function call">int()</error>

for <error descr="Cannot assign to literal">1</error> in []:
  pass

for (<error descr="Cannot assign to literal">1</error>,(<error descr="Cannot assign to literal">2</error>,)) in [12]:
  pass

<error descr="Augmented assign to dict comprehension not possible">{ x: y for y, x in ((1, 2), (3, 4)) }</error> += 5
<error descr="Cannot assign to set comprehension">{ x for x in (1, 2) }</error> = 5

# ok

for (a,b) in []:
  pass

a[1] = 1

[a, b] = 1, 2

[foo()[1], (c, d.e)] = 1, 2, 3


z = None
