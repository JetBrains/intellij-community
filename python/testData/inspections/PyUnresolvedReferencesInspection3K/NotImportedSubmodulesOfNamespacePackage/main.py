import nspkg1.m2

print(nspkg1.m2)
print(nspkg1.<warning descr="Cannot find reference 'm3' in 'imported module nspkg1'">m3</warning>)
print(nspkg1.<warning descr="Cannot find reference 'nssubpkg1' in 'imported module nspkg1'">nssubpkg1</warning>)
