from six import PY2

if PY2:
    def ascii(obj):
        ...
a = ascii(42) + 'foo'


def func(p):
    X = a