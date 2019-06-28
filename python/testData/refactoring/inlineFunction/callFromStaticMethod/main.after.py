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
        print(my_a.a)
        print(my_a.b)
        res = my_a.a + my_a.b