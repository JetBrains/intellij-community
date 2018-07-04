from typing import NamedTuple

MyTuple = NamedTuple('MyTuple', [('foo', int)])


def func():
    return MyTuple
