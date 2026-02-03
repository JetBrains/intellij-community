class A(object):
    my_attr = 17

class B(A):
    pass


b = B()
print(b.my_attr)
#         <ref>