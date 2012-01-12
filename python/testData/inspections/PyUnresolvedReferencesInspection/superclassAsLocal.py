class A(object):
    def method(self):
        pass
C = A

class B(C):
    pass

b = B()
b.method() #Unresolved attribute reference 'method' for class 'B'