def f(x):
    return x

class C(object):
    __div__, __rdiv__ = f(0)

c = C()
print(c / 2)
