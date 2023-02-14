class Clazz:
    def __init__(self, alpha):
        self.alpha = alpha

    def foo(self):
        local_var = 42
        self.x<caret> = 2 + local_var
