class Example1:
    def __init__(self):
        self.field1 = 1


class Example2(Example1):
    def __init__(self):  # Some valuable comment here
        Example1.__init__(self)