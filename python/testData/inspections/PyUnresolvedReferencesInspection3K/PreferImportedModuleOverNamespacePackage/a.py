import c

print(c.A().foo())
print(c.<warning descr="Cannot find reference 'b' in 'c.py'">b</warning>.A().foo())