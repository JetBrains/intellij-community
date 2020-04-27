class A(object):
    def __init__(self, x):
        self.x = x

    def get_x(self):
        return self.x


a = A(42).get_x()
