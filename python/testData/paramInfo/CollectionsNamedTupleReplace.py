from collections import namedtuple


MyTup1 = namedtuple("MyTup1", "bar baz")


class MyTup2(namedtuple("MyTup2", "bar baz")):
    pass


MyTup1(1, 2)._replace(<arg1>)
MyTup2(1, 2)._replace(<arg2>)

MyTup1._replace(MyTup1(1, 2), <arg3>)
MyTup2._replace(MyTup2(1, 2), <arg4>)