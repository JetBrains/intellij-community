class A1(object):
    def method1(self):
        pass

class A2(A1):
    def method2(self):
        print 'm2'

class B(A2):
    def method2(self):
        super(A2, self).<warning descr="Unresolved attribute reference 'method2' for class 'A1'">method2</warning>()   #method2 should be highlighted as unresolved.

B().method2()