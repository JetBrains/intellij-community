from A import A as myA  # No problem without the 'as'


class B(object):
    pass


class C(myA, B):  # No problem when only inheriting from myA
    pass
