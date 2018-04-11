x = default_value
y = 0
if True:
    <warning descr="Redeclared 'x' defined above without usage">x</warning> = y
else:
    pass
<warning descr="Redeclared 'x' defined above without usage">x</warning> = y * 2