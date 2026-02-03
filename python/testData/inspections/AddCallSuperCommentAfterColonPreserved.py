class Example1:
    def __init__(self):
        self.field1 = 1


class Example2(Example1):
    def <warning descr="Call to __init__ of super class is missed">__init<caret>__</warning>(self):  # Some valuable comment here
        pass