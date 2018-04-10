import time


class Foo(object):
    def __init__(self, name):
        self.name = name

    def __repr__(self):
        time.sleep(1)
        return self.name


f = Foo("foo")
l = [Foo("list"), Foo("list")]
a = 1