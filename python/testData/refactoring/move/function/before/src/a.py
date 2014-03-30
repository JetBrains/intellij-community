from lib1 import urlopen


def f(url):
    '''Return the representation available at the URL.

    '''
    return urlopen(url).read()


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