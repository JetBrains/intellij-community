class A(object):
    __class__ = 17

class B(A):
    print(__class__)
#           <ref>