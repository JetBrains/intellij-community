class A:
    x = 1
    y = 1

class B(A):
    def foo(self):
        self.__repr__()
#               <ref>
   