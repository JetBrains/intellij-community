class A(object):
    __sizeof__ = 17

class B(A):
    print(__sizeof__)
#           <ref>