class C:
    def me<caret>thod(self, foo, foo1, bar):
        print(self.foo, self.bar)
        

C().method(1, 2, bar=3)