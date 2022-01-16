class Clazz:
    def __init__(self, alpha):
        self.alpha = alpha

    def foo(self):
        self.x<caret> = self.bar(42)

    def bar(self, x):
        print(x)
        return x + 1
