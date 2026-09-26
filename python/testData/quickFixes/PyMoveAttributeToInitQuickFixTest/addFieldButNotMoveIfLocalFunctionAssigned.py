class Clazz:
    def __init__(self, alpha):
        self.alpha = alpha

    def foo(self):
        def local_fun():
            return 42
        self.x<caret> = 2 + local_fun()
