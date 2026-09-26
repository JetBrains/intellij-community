class A:
    def __init__(self, a, b):
        self.a = a
        self.b = b

    def doStuff(self):
        print(self.a)
        print(self.b)
        return self.a + self.b


my_a = A(1, 2)
res = A.doSt<caret>uff(my_a)
