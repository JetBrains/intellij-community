def f(x):
    def d(f):
        return f
    @d #pass
    def g(d):
        return d
    return g(x)
