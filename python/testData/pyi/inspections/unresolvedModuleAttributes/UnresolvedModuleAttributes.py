import m1


print(m1.module_attr)
print(m1.provided_attr)
print(m1.<warning descr="Cannot find reference 'not_provided_attr' in 'm1.py'">not_provided_attr</warning>)


print(m1.<warning descr="Cannot find reference 'm2' in 'm1.py'">m2</warning>)
print(m1.<warning descr="Cannot find reference 'm3' in 'm1.py'">m3</warning>)
print(m1.m3_imported_as)
