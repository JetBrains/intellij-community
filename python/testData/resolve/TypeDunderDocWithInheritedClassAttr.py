class A(object):
    __doc__ = 17

class B(A):
    pass


print(B.__doc__)
#         <ref>