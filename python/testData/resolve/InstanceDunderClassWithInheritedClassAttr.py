class A(object):
    __class__ = 15

class B(A):
    pass


b = B()
print(b.__class__)
#         <ref>