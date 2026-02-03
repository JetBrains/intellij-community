class C:
    def __init__(self, foo):
        self.foo = foo
    
    def me<caret>thod(self, foo):
        self.method(self.foo)
        C(1).method(2)
