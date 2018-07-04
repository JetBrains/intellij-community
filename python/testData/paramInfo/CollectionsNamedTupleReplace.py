from collections import namedtuple


MyTup1 = namedtuple("MyTup1", "bar baz")


class MyTup2(namedtuple("MyTup2", "bar baz")):
    pass


MyTup1(1, 2)._replace(<arg1>)
MyTup2(1, 2)._replace(<arg2>)