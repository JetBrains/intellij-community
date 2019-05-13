import m1


print(m1.<warning descr="Cannot find reference 'module_only_attr' in 'm1.pyi'">module_only_attr</warning>)
print(m1.provided_attr)
print(m1.<warning descr="Cannot find reference 'not_provided_attr' in 'm1.pyi'">not_provided_attr</warning>)
