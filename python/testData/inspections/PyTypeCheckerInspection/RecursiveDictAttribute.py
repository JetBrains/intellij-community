class C:
    def f(self, x):
        self.foo = x
        self.foo = {'foo': self.foo}
        return self.foo['foo'] + 10
