from six import with_metaclass as w_m

class M(type):
    pass


class B(object):
   pass


class D(object):
    pass


class C(w_m(M, B, D)):
    pass