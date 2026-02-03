class A(object):
    __sizeof__ = 17

class B(A):
    pass


print(B.__sizeof__)
#         <ref>