from collections import namedtuple


class C(namedtuple('Coord', 'latitude longitude')):
    def foo(self):
        return -1


c = C()
c.latitude
