class C:
    def __init__(self):
        self.foo = 42
        self.bar = 'spam'

    def me<caret>thod(self, x):
        print(self.foo, self.bar)


C().method(1)