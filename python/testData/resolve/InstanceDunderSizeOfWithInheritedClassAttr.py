class A(object):
    __sizeof__ = 17

class B(A):
    pass


b = B()
print(b.__sizeof__)
#         <ref>