import six

class M(type):
    pass


class B(object):
   pass


class D(object):
    pass


class C(six.with_metaclass(M, B, D)):
    pass