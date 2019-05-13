class A(object):
    __doc__ = 16

class B(A):
    pass


b = B()
print(b.__doc__)
#         <ref>