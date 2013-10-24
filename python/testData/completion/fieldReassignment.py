class C1(object):
    def method1(self):
        pass

class Test(object):
    def __init__(self, x):
        self.x = x
        self.x = C1()
        self.x.meth<caret>