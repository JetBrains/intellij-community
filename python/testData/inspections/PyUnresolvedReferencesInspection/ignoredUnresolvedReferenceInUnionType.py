class A:
    pass

a = A()
print(a.f<caret>oo)

def func(c):
    x = A() if c else None
    return x.foo