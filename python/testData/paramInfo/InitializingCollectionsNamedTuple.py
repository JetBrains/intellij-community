from collections import namedtuple


MyTup1 = namedtuple("MyTup1", "bar baz")


class MyTup2(namedtuple("MyTup2", "bar baz")):
    pass


MyTup1(<arg1>)
MyTup2(<arg2>)