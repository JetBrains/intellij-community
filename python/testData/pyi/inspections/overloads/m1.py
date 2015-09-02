class C(object):
    def __getitem__(self, key):
        return f(key)

    def __add__(self, other):
        return f(other)


def f(key):
    return key


def g(x):
    pass
