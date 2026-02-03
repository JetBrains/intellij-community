class C:
    def __init__(self):
        self.foo = 42

    def me<caret>thod(self, x):
        print(self.foo)


C().method(1)
