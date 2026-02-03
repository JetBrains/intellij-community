class C(object):
    pass


class D(object):
    def f(self):
        def g(x):
            x = C()
            x.y = 1 #pass
            return x
        return g
