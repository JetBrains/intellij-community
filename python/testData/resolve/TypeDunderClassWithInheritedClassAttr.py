class A(object):
    __class__ = 17

class B(A):
    pass


print(B.__class__)
#         <ref>