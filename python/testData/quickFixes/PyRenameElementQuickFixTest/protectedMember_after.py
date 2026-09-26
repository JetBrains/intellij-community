

class A:
    def __init__(self):
        self.A_NEW_NAME = 1

    def _foo(self):
        pass


a_class = A()
a_class._foo()
print(a_class.A_NEW_NAME)

