def f():
    z = 2
    def g(z=z): #pass
        return z
    return g
