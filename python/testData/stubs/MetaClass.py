class M(type):
    pass


__metaclass__ = M


class C(object):
    __metaclass__ = type


class D(object):
    pass
