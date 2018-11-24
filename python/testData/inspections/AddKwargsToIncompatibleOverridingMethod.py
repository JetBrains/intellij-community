class Base:
    def m(self, x):
        pass
    
    
class Sub(Base):
    def m<warning descr="Signature of method 'Sub.m()' does not match signature of base method in class 'Base'">(<caret>self)</warning>:
        pass