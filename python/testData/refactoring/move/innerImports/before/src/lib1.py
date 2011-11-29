def I(x):
    return x

def K(x):
    return lambda y: x

S = lambda f: lambda g: lambda x: (f(x))(g(x))
