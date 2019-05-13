class A:
    def __init__(self, z):
        self.z = z

    def foo(self, x):
        y = 2 * x + self.z
        return 1 + y


def zoo(x):
    y = int((x - 2) / (x - 1))

    return A(y)

print(zoo(2).foo(2))

try:
    try:
        print(zoo(1).foo(2)) #we got ZeroDivision here
    finally:
        print(zoo(0).foo(2))
except:
    pass

a = zoo(-1)
print(a.foo(2))