from collections import namedtuple


MyTup1 = namedtuple("MyTup1", "bar baz")


class MyTup2(namedtuple("MyTup2", "bar baz")):
    pass


class MyTup3(namedtuple("MyTup3", "bar baz")):

    @classmethod
    def factory(cls):
        return cls(<arg3>)


MyTup1(<arg1>)
MyTup2(<arg2>)