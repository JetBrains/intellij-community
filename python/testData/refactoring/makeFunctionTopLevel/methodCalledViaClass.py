class C():
    def me<caret>thod(self, x):
        print(self.foo, self.bar, x)
        
        
C.method(C(), 42)
C.method()