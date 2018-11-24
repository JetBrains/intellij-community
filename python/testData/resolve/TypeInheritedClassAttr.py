class A(object):
    my_attr = 17

class B(A):
    pass


print(B.my_attr)
#         <ref>