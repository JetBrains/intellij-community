class A:
    def __init__(self, a, b):
        self.a = a
        self.b = b

    def doStuff(self):
        print(self.a)
        print(self.b)
        return self.a + self.b

    @staticmethod
    def foo():
        my_a = A(1, 2)
        res = my_a.doSt<caret>uff()