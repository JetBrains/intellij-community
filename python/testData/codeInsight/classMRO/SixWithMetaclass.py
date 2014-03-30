class M(type):
    pass


class B(object):
   pass


class C(six.with_metaclass(M, B)):
    pass