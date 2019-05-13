class Base():
    def __init__(self):
        self.param = 2

class Child(Base):
    def __init__(self):
        super().__init__()
        self.my = 1

    def f(self):
        pass