class A:
    pass

a = A()
print(a.f<caret>oo)

def func(c):
    x = A() if c else None
    return x.foo

# Sentinel reference to make sure that other warnings are still present
<error descr="Unresolved reference 'UNRESOLVED'">UNRESOLVED</error>