class A:
    def __init__(self):
        self.x = 1


a = A()
a.<warning descr="Unresolved attribute reference 'y' for class 'A'">y</warning>()
