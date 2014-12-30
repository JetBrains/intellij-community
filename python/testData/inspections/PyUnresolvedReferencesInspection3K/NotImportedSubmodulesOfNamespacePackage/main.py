import nspkg1.m2

print(nspkg1.m2)
print(nspkg1.<weak_warning descr="Cannot find reference 'm3' in 'imported module nspkg1'">m3</weak_warning>)
print(nspkg1.<warning descr="Cannot find reference 'm4' in 'imported module nspkg1'">m4</warning>)
print(nspkg1.<weak_warning descr="Cannot find reference 'nssubpkg1' in 'imported module nspkg1'">nssubpkg1</weak_warning>)
print(nspkg1.<warning descr="Cannot find reference 'nssubpkg2' in 'imported module nspkg1'">nssubpkg2</warning>)
