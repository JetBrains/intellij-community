def f(x):
    z = 42
    g(x, z, 42)


def g(x, z, y):
    print(x, y, z)
    g(x, z, x)