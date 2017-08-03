class Ancestor(object):

    def __init__(self, a, b):
        self.a = a
        self.b = b

    def func1(self):
        return ""


class Basic(Ancestor):

    def func2(self):
        return self.func1()