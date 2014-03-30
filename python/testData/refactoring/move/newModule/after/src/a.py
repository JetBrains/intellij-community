from b import f


def f_usage():
    return f(14)


class C(object):
    def g(self, x):
        return x


class D(C):
    def g(self, x, y):
        return super(D, self).f(x) + y


class E(object):
    def g(self):
        return -1