class Example1:
    def __init__(self, field1: str):
        self.field1 = field1


class Example2(Example1):
    def __init__(self, field1: str):
        super().__init__(field1)