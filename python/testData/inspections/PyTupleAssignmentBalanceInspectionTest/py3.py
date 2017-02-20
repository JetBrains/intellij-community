a, b, c = <warning descr="Too many values to unpack">1, 2, 3, 4</warning>
a, b, c = <warning descr="Need more values to unpack">foo, bar</warning>
a, b, c, d = 1, 2, 3, 4
a = 1, 2, 3, 4
a, b, c = <warning descr="Need more values to unpack">2</warning>
*a, b = 1, 2, 3
*a, b, c = 1, 2
b, c, *a, d = <warning descr="Need more values to unpack">1, 2</warning>
*a, b = 1, 2
<warning descr="Only one starred expression allowed in assignment">a, *b, c, *d</warning> = 1, 2, 3, 4, 5, 6