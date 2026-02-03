class C(object):
    def f(self, x):
        self.x = x

    def __str__(self):
        return self.x