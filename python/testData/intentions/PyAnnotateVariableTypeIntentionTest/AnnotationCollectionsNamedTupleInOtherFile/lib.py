from collections import namedtuple

MyTuple = namedtuple('MyTuple', ['foo'])


def func():
    return MyTuple(foo=42)
