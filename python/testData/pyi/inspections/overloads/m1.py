class C(object):
    def __getitem__(self, key):
        return f(key)


def f(key):
    return key


def g(x):
    pass
