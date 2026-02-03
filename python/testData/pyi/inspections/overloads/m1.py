class C(object):
    def __getitem__(self, key):
        return f(key)

    def __add__(self, other):
        return f(other)


def f(key):
    return key


def g(x):
    pass


class Gen(object):
    def __init__(self, x):
        self.x = x

    def get(self, x, y):
        return self.x
